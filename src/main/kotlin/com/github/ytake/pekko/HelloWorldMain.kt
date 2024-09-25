package com.github.ytake.pekko

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive

class HelloWorldMain(
    context: ActorContext<SayHello>
) : AbstractBehavior<HelloWorldMain.SayHello>(context) {

    data class SayHello(val name: String)

    companion object {
        fun create(): Behavior<SayHello> {
            return Behaviors.setup(::HelloWorldMain)
        }
    }

    private val greeter: ActorRef<HelloWorld.Greet> = context.spawn(HelloWorld.create(), "greeter")

    override fun createReceive(): Receive<SayHello> {
        return newReceiveBuilder()
            .onMessage(SayHello::class.java, this::onStart)
            .build()
    }

    private fun onStart(command: SayHello): Behavior<SayHello> {
        val replyTo: ActorRef<HelloWorld.Greeted> = context.spawn(HelloWorldBot.create(3), command.name)
        greeter.tell(HelloWorld.Greet(command.name, replyTo))
        return this
    }
}