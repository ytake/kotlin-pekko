package com.github.ytake.persistence

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.pattern.StatusReply
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.javadsl.CommandHandler
import org.apache.pekko.persistence.typed.javadsl.CommandHandlerBuilder
import org.apache.pekko.persistence.typed.javadsl.Effect
import org.apache.pekko.persistence.typed.javadsl.EventHandler
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior
import org.apache.pekko.persistence.typed.javadsl.RetentionCriteria
import com.fasterxml.jackson.annotation.JsonCreator
import java.time.Duration
import java.time.Instant

class ShoppingCart(
    val cartId: String
) : EventSourcedBehavior<ShoppingCart.Command, ShoppingCart.Event, ShoppingCart.State>(
    PersistenceId.of("ShoppingCart", cartId),
    SupervisorStrategy.restartWithBackoff(Duration.ofMillis(200), Duration.ofSeconds(5), 0.1)
) {

    data class State(
        val items: Map<String, Int> = mapOf(),
        val checkoutDate: Instant? = null
    ) : CborSerializable {

        val isCheckedOut: Boolean = checkoutDate != null

        val isEmpty: Boolean = items.isEmpty()

        fun hasItem(itemId: String): Boolean = items.containsKey(itemId)

        fun updateItem(itemId: String, quantity: Int): State {
            val mutableItems = items.toMutableMap();
            if (quantity == 0) mutableItems.remove(itemId) else mutableItems[itemId] = quantity
            return State(items = mutableItems.toMap(), checkoutDate)
        }

        fun removeItem(itemId: String): State {
            val mutableItems = items.toMutableMap();
            mutableItems.remove(itemId)
            return State(items = mutableItems.toMap(), checkoutDate)
        }

        fun checkout(now: Instant): State {
            return State(items, checkoutDate = now)
        }

        val toSummary: Summary = Summary(items, isCheckedOut)
    }

    interface Command : CborSerializable

    data class AddItem @JsonCreator constructor(
        val itemId: String,
        val quantity: Int,
        val replyTo: ActorRef<StatusReply<Summary>>
    ) : Command

    data class RemoveItem @JsonCreator constructor(
        val itemId: String,
        val replyTo: ActorRef<StatusReply<Summary>>
    ) : Command

    data class AdjustItemQuantity @JsonCreator constructor(
        val itemId: String,
        val quantity: Int,
        val replyTo: ActorRef<StatusReply<Summary>>
    ) : Command

    data class Get @JsonCreator constructor(
        val replyTo: ActorRef<Summary>
    ) : Command

    data class Checkout @JsonCreator constructor(
        val replyTo: ActorRef<StatusReply<Summary>>
    ) : Command

    data class Summary(
        val items: Map<String, Int>,
        val checkedOut: Boolean
    ) : CborSerializable

    interface Event : CborSerializable

    data class ItemAdded(
        val cartId: String,
        val itemId: String,
        val quantity: Int
    ) : Event

    data class ItemRemoved(
        val cartId: String,
        val itemId: String
    ) : Event

    data class ItemQuantityAdjusted(
        val cartId: String,
        val itemId: String,
        val quantity: Int
    ) : Event

    data class CheckedOut(
        val cartId: String,
        val eventTime: Instant
    ) : Event

    override fun emptyState(): State = State()

    override fun commandHandler(): CommandHandler<Command, Event, State> {
        val b: CommandHandlerBuilder<Command, Event, State> = newCommandHandlerBuilder()

        val openHandlers = OpenShoppingCartCommandHandlers()
        val checkedOutHandlers = CheckedOutCommandHandlers()

        b.forState { !it.isCheckedOut }
            .onCommand(AddItem::class.java, openHandlers::onAddItem)
            .onCommand(RemoveItem::class.java, openHandlers::onRemoveItem)
            .onCommand(AdjustItemQuantity::class.java, openHandlers::onAdjustItemQuantity)
            .onCommand(Checkout::class.java, openHandlers::onCheckout)

        b.forState { it.isCheckedOut }
            .onCommand(AddItem::class.java, checkedOutHandlers::onAddItem)
            .onCommand(RemoveItem::class.java, checkedOutHandlers::onRemoveItem)
            .onCommand(AdjustItemQuantity::class.java, checkedOutHandlers::onAdjustItemQuantity)
            .onCommand(Checkout::class.java, checkedOutHandlers::onCheckout)

        b.forAnyState()
            .onCommand(Get::class.java, ::onGet)

        return b.build()
    }

    private fun onGet(state: State, cmd: Get): Effect<Event, State> {
        cmd.replyTo.tell(state.toSummary)
        return Effect().none()
    }

    private inner class OpenShoppingCartCommandHandlers {
        fun onAddItem(state: State, cmd: AddItem): Effect<Event, State> {
            return when {
                state.hasItem(cmd.itemId) -> {
                    cmd.replyTo.tell(StatusReply.error("Item '${cmd.itemId}' was already added to this shopping cart"))
                    Effect().none()
                }

                cmd.quantity <= 0 -> {
                    cmd.replyTo.tell(StatusReply.error("Quantity must be greater than zero"))
                    Effect().none()
                }

                else -> {
                    Effect().persist(ItemAdded(cartId, cmd.itemId, cmd.quantity))
                        .thenRun { updatedCart: State -> cmd.replyTo.tell(StatusReply.success(updatedCart.toSummary)) }
                }
            }
        }

        fun onRemoveItem(state: State, cmd: RemoveItem): Effect<Event, State> {
            return if (state.hasItem(cmd.itemId)) {
                Effect().persist(ItemRemoved(cartId, cmd.itemId))
                    .thenRun { updatedCart: State -> cmd.replyTo.tell(StatusReply.success(updatedCart.toSummary)) }
            } else {
                cmd.replyTo.tell(StatusReply.success(state.toSummary))
                Effect().none()
            }
        }

        fun onAdjustItemQuantity(state: State, cmd: AdjustItemQuantity): Effect<Event, State> {
            return when {
                cmd.quantity <= 0 -> {
                    cmd.replyTo.tell(StatusReply.error("Quantity must be greater than zero"))
                    Effect().none()
                }

                state.hasItem(cmd.itemId) -> {
                    Effect().persist(ItemQuantityAdjusted(cartId, cmd.itemId, cmd.quantity))
                        .thenRun { updatedCart: State -> cmd.replyTo.tell(StatusReply.success(updatedCart.toSummary)) }
                }

                else -> {
                    cmd.replyTo.tell(StatusReply.error("Cannot adjust quantity for item '${cmd.itemId}'. Item not present on cart"))
                    Effect().none()
                }
            }
        }

        fun onCheckout(state: State, cmd: Checkout): Effect<Event, State> {
            return if (state.isEmpty) {
                cmd.replyTo.tell(StatusReply.error("Cannot checkout an empty shopping cart"))
                Effect().none()
            } else {
                Effect().persist(CheckedOut(cartId, Instant.now()))
                    .thenRun { updatedCart: State -> cmd.replyTo.tell(StatusReply.success(updatedCart.toSummary)) }
            }
        }
    }

    private inner class CheckedOutCommandHandlers {
        fun onAddItem(cmd: AddItem): Effect<Event, State> {
            cmd.replyTo.tell(StatusReply.error("Can't add an item to an already checked out shopping cart"))
            return Effect().none()
        }

        fun onRemoveItem(cmd: RemoveItem): Effect<Event, State> {
            cmd.replyTo.tell(StatusReply.error("Can't remove an item from an already checked out shopping cart"))
            return Effect().none()
        }

        fun onAdjustItemQuantity(cmd: AdjustItemQuantity): Effect<Event, State> {
            cmd.replyTo.tell(StatusReply.error("Can't adjust item on an already checked out shopping cart"))
            return Effect().none()
        }

        fun onCheckout(cmd: Checkout): Effect<Event, State> {
            cmd.replyTo.tell(StatusReply.error("Can't checkout already checked out shopping cart"))
            return Effect().none()
        }
    }

    override fun eventHandler(): EventHandler<State, Event> {
        return newEventHandlerBuilder().forAnyState()
            .onEvent(ItemAdded::class.java) { state, event -> state.updateItem(event.itemId, event.quantity) }
            .onEvent(ItemRemoved::class.java) { state, event -> state.removeItem(event.itemId) }
            .onEvent(ItemQuantityAdjusted::class.java) { state, event ->
                state.updateItem(
                    event.itemId,
                    event.quantity
                )
            }
            .onEvent(CheckedOut::class.java) { state, event -> state.checkout(event.eventTime) }
            .build()
    }

    override fun retentionCriteria(): RetentionCriteria {
        return RetentionCriteria.snapshotEvery(100, 3)
    }

    companion object {
        fun create(cartId: String): Behavior<Command> = ShoppingCart(cartId)
    }
}
