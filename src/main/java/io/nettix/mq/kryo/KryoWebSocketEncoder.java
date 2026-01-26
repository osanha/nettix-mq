

package io.nettix.mq.kryo;

import nf.fr.eraasoft.pool.ObjectPool;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import com.esotericsoftware.kryo.Kryo;

/**
 * Encodes Kryo-serialized data into WebSocket BinaryFrame.
 *
 * @author sanha
 */
@Sharable
public class KryoWebSocketEncoder
    extends KryoEncoder
{
  /**
   * Constructor.
   *
   * @param pool
   *          the Kryo object pool
   */
  public KryoWebSocketEncoder(ObjectPool<Kryo> pool)
  {
    super(pool);
  }

  @Override
  protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg)
      throws Exception
  {
    ChannelBuffer buf = (ChannelBuffer) super.encode(ctx, channel, msg);
    return new BinaryWebSocketFrame(buf);
  }

}
