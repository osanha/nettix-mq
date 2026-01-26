

package io.nettix.mq;

/**
 * Message used in the message queue.
 *
 * @author sanha
 */
public class Message
{
  /**
   * Message sequence number. Used only for synchronous messaging.
   */
  Integer seq;

  /**
   * Message type.
   */
  int type;

  /**
   * Message value.
   */
  Object value;

  /**
   * Constructor. Required for Kryo serialization.
   */
  Message()
  {
  }

  /**
   * Constructor.
   *
   * @param type
   *          message type
   * @param value
   *          message value
   */
  Message(int type, Object value)
  {
    this(null, type, value);
  }

  /**
   * Constructor.
   *
   * @param seq
   *          message sequence number
   * @param type
   *          message type
   * @param value
   *          message value
   */
  Message(Integer seq, int type, Object value)
  {
    this.seq = seq;
    this.type = type;
    this.value = value;
  }

}
