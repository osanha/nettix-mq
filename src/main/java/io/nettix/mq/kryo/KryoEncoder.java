

package io.nettix.mq.kryo;

import nf.fr.eraasoft.pool.ObjectPool;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

/**
 * Serializes objects using Kryo.
 *
 * @author sanha
 */
@Sharable
public class KryoEncoder
    extends OneToOneEncoder
{
  /**
   * Kryo object pool.
   */
  private final ObjectPool<Kryo> _pool;

  /**
   * Constructor.
   *
   * @param pool
   *          the Kryo object pool
   */
  public KryoEncoder(ObjectPool<Kryo> pool)
  {
    _pool = pool;
  }

  @Override
  protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg)
      throws Exception
  {
    Kryo kryo = _pool.getObj();

    try
      {
        ChannelBuffer buffer = ChannelBuffers.dynamicBuffer();
        ChannelBufferOutputStream os = new ChannelBufferOutputStream(buffer);
        Output output = new Output(os);
        kryo.writeObject(output, msg);
        output.close();
        return buffer;
      }
    finally
      {
        _pool.returnObj(kryo);
      }
  }

}
