

package io.nettix.mq.kryo;

import nf.fr.eraasoft.pool.ObjectPool;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import com.esotericsoftware.kryo.Kryo;

/**
 * Deserializes WebSocket BinaryFrame data into objects using Kryo.
 *
 * @author sanha
 */
public class KryoWebSocketDecoder
    extends KryoDecoder
{
  /**
   * Constructor.
   *
   * @param pool
   *          the Kryo object pool
   * @param type
   *          the type of object to deserialize
   */
  public KryoWebSocketDecoder(ObjectPool<Kryo> pool, Class<?> type)
  {
    super(pool, type);
  }

  @Override
  protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg)
      throws Exception
  {
    if (msg instanceof BinaryWebSocketFrame)
      {
        BinaryWebSocketFrame frame = (BinaryWebSocketFrame) msg;
        return super.decode(ctx, channel, frame.getBinaryData());
      }

    throw new RuntimeException("Invalid WebSocketFrame - "
                               + msg.getClass().getSimpleName());
  }

}
