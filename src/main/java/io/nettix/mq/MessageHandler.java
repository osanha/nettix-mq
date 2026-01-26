

package io.nettix.mq;

import org.jboss.netty.channel.Channel;

/**
 * Message handler with void return type.
 *
 * @author sanha
 *
 * @param <V>
 *          message type
 */
public abstract class MessageHandler<V>
    implements MessageListener<V, Void>
{
  @Override
  public Void messageReceived(Channel ch, V value) throws Exception
  {
    handleMessage(ch, value);
    return null;
  }

  /**
   * Handles the message.
   *
   * @param ch
   *          the receiving channel
   * @param value
   *          the message
   * @throws Exception
   */
  public abstract void handleMessage(Channel ch, V value) throws Exception;
}
