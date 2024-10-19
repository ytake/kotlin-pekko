package com.github.ytake.persistence

import org.apache.pekko.actor.testkit.typed.javadsl.TestKitJunitResource
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.pattern.StatusReply
import org.junit.ClassRule
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ShoppingCartTest {

    companion object {
        @JvmField
        @ClassRule
        val testKit = TestKitJunitResource(
            """
            pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
            pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
            pekko.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID()}"
            """.trimIndent()
        )
        private val counter = AtomicInteger()

        private fun newCartId(): String {
            return "cart-${counter.incrementAndGet()}"
        }
    }

    @Test
    fun `should add item`() {
        val cart: ActorRef<ShoppingCart.Command> = testKit.spawn(ShoppingCart.create(newCartId()))
        val probe = testKit.createTestProbe<StatusReply<ShoppingCart.Summary>>()
        cart.tell(ShoppingCart.AddItem("foo", 42, probe.ref))
        val result = probe.receiveMessage()
        assertEquals(42, result.value.items["foo"])
        assertFalse(result.value.checkedOut)
    }

    @Test
    fun `should reject already added item`() {
        val cart: ActorRef<ShoppingCart.Command> = testKit.spawn(ShoppingCart.create(newCartId()))
        val probe = testKit.createTestProbe<StatusReply<ShoppingCart.Summary>>()
        cart.tell(ShoppingCart.AddItem("foo", 42, probe.ref))
        assertTrue(probe.receiveMessage().isSuccess)
        cart.tell(ShoppingCart.AddItem("foo", 13, probe.ref))
        assertTrue(probe.receiveMessage().isError)
    }

    @Test
    fun `should remove item`() {
        val cart: ActorRef<ShoppingCart.Command> = testKit.spawn(ShoppingCart.create(newCartId()))
        val probe = testKit.createTestProbe<StatusReply<ShoppingCart.Summary>>()
        cart.tell(ShoppingCart.AddItem("foo", 42, probe.ref))
        assertTrue(probe.receiveMessage().isSuccess)
        cart.tell(ShoppingCart.RemoveItem("foo", probe.ref))
        val result = probe.receiveMessage()
        assertTrue(result.isSuccess)
        assertTrue(result.value.items.isEmpty())
    }

    @Test
    fun `should adjust quantity`() {
        val cart: ActorRef<ShoppingCart.Command> = testKit.spawn(ShoppingCart.create(newCartId()))
        val probe = testKit.createTestProbe<StatusReply<ShoppingCart.Summary>>()
        cart.tell(ShoppingCart.AddItem("foo", 42, probe.ref))
        probe.receiveMessage()
        cart.tell(ShoppingCart.AdjustItemQuantity("foo", 43, probe.ref))
        val result = probe.receiveMessage()
        assertTrue(result.isSuccess)
        assertEquals(43, result.value.items["foo"])
    }

    @Test
    fun `should checkout`() {
        val cart: ActorRef<ShoppingCart.Command> = testKit.spawn(ShoppingCart.create(newCartId()))
        val probe = testKit.createTestProbe<StatusReply<ShoppingCart.Summary>>()
        cart.tell(ShoppingCart.AddItem("foo", 42, probe.ref))
        assertTrue(probe.receiveMessage().isSuccess)
        cart.tell(ShoppingCart.Checkout(probe.ref))
        val result = probe.receiveMessage()
        assertTrue(result.isSuccess)
        assertTrue(result.value.checkedOut)

        cart.tell(ShoppingCart.AddItem("bar", 13, probe.ref))
        assertTrue(probe.receiveMessage().isError)
    }

    @Test
    fun `should keep its state`() {
        val cartId = newCartId()
        val cart: ActorRef<ShoppingCart.Command> = testKit.spawn(ShoppingCart.create(cartId))
        val probe = testKit.createTestProbe<StatusReply<ShoppingCart.Summary>>()
        cart.tell(ShoppingCart.AddItem("foo", 42, probe.ref))
        val result = probe.receiveMessage()
        assertTrue(result.isSuccess)
        assertEquals(42, result.value.items["foo"])

        testKit.stop(cart)

        // start again with same cartId
        val restartedCart: ActorRef<ShoppingCart.Command> = testKit.spawn(ShoppingCart.create(cartId))
        val stateProbe = testKit.createTestProbe<ShoppingCart.Summary>()
        restartedCart.tell(ShoppingCart.Get(stateProbe.ref))
        val state = stateProbe.receiveMessage()
        assertEquals(42, state.items["foo"])
    }
}
