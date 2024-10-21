package com.github.ytake.pekko

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

class HelloWorldBot(
    context: ActorContext<HelloWorld.Greeted>,
    private val max: Int,
    private val greetingCounter: Int
) : AbstractBehavior<HelloWorld.Greeted>(context) {

    companion object {
        fun create(max: Int): Behavior<HelloWorld.Greeted> {
            return Behaviors.setup { context -> HelloWorldBot(context, max, 0) }
        }
    }

    override fun createReceive(): Receive<HelloWorld.Greeted> {
        return newReceiveBuilder()
            .onMessage(HelloWorld.Greeted::class.java, this::onGreeted)
            .build()
    }

    private fun onGreeted(message: HelloWorld.Greeted): Behavior<HelloWorld.Greeted> {
        val newGreetingCounter = greetingCounter + 1
        context.log.info("Greeting {} for {}", newGreetingCounter, message.whom)
        return if (newGreetingCounter == max) {
            Behaviors.stopped()
        } else {
            message.from.tell(HelloWorld.Greet(message.whom, context.self))
            HelloWorldBot(context, max, newGreetingCounter)
        }
    }
}
