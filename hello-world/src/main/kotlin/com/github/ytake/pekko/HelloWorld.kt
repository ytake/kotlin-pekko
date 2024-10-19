package com.github.ytake.pekko

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

class HelloWorld(
    context: ActorContext<Greet>
) : AbstractBehavior<HelloWorld.Greet>(context) {

    data class Greet(val whom: String, val replyTo: ActorRef<Greeted>)

    data class Greeted(val whom: String, val from: ActorRef<Greet>)

    companion object {
        fun create(): Behavior<Greet> {
            return Behaviors.setup(::HelloWorld)
        }
    }

    override fun createReceive(): Receive<Greet> {
        return newReceiveBuilder().onMessage(Greet::class.java, this::onGreet).build()
    }

    private fun onGreet(command: Greet): Behavior<Greet> {
        context.log.info("Hello {}!", command.whom)
        command.replyTo.tell(Greeted(command.whom, context.self))
        return this
    }
}
