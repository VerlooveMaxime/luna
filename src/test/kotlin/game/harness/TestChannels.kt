package game.harness

import io.luna.net.msg.GameMessage
import io.luna.net.msg.GameMessageWriter
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress

/** An embedded channel with a socket peer, which Luna's `Client` needs to read the client's IP address. */
private class ClientChannel : EmbeddedChannel() {
    override fun remoteAddress(): SocketAddress = InetSocketAddress(InetAddress.getLoopbackAddress(), 43594)
}

/** A channel laid out like a logged-in client's, with a pass-through handler where `game-encoder` is. */
fun loggedInChannel(): EmbeddedChannel =
    ClientChannel().apply { pipeline().addLast("game-encoder", ChannelOutboundHandlerAdapter()) }

/** [writer] encoded the way `GameClient.queue` encodes it; its payload is a pooled buffer the caller releases. */
fun encode(writer: GameMessageWriter): GameMessage = checkNotNull(writer.toGameMessage(null))
