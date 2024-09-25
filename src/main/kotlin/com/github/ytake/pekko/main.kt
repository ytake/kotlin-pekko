package com.github.ytake.pekko

import com.github.ytake.pekko.HelloWorldMain.SayHello
import org.apache.pekko.actor.typed.ActorSystem

fun main(args: Array<String>) {
    val system: ActorSystem<SayHello> =
        ActorSystem.create(HelloWorldMain.create(), "hello")

    system.tell(SayHello("World"))
    system.tell(SayHello("Pekko"))
}
