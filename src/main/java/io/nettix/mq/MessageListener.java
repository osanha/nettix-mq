

package io.nettix.mq;

import org.jboss.netty.channel.Channel;


/**
 * Message listener.
 *
 * @author sanha
 *
 * @param <V>
 *          message type
 * @param <T>
 *          return type
 */
public interface MessageListener<V, T>
{
  /**
   * Handles received messages.
   *
   * @param ch
   *          the channel
   * @param value
   *          the message
   * @return the response for synchronous messages
   * @throws Exception
   */
  T messageReceived(Channel ch, V value) throws Exception;
}
