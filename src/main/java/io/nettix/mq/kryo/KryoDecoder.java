

package io.nettix.mq.kryo;

import java.io.InputStream;

import nf.fr.eraasoft.pool.ObjectPool;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;

/**
 * Deserializes objects using Kryo.
 *
 * @author sanha
 */
public class KryoDecoder
    extends OneToOneDecoder
{
  /**
   * Kryo object pool.
   */
  private final ObjectPool<Kryo> _pool;

  /**
   * The type of object to deserialize.
   */
  private final Class<?> _type;

  /**
   * Constructor.
   *
   * @param pool
   *          the Kryo object pool
   * @param type
   *          the type of object to deserialize
   */
  public KryoDecoder(ObjectPool<Kryo> pool, Class<?> type)
  {
    _pool = pool;
    _type = type;
  }

  @Override
  protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg)
      throws Exception
  {
    Kryo kryo = _pool.getObj();

    try
      {
        ChannelBuffer buffer = (ChannelBuffer) msg;
        InputStream is = new ChannelBufferInputStream(buffer);
        Input input = new Input(is);
        Object obj = kryo.readObject(input, _type);
        input.close();
        return obj;
      }
    finally
      {
        _pool.returnObj(kryo);
      }
  }

}
