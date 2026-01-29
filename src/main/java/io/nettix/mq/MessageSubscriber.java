

package io.nettix.mq;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import javax.management.ListenerNotFoundException;

import nf.fr.eraasoft.pool.ObjectPool;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nettix.channel.PersistentClientChannelManager;
import io.nettix.channel.handler.ConnectStateEventHandler;
import io.nettix.channel.handler.InboundMessageHandler;
import io.nettix.mq.kryo.KryoWebSocketDecoder;
import io.nettix.mq.kryo.KryoWebSocketEncoder;
import io.nettix.mq.kryo.PoolableKryoFactory;
import io.nettix.websocket.AbstractWebSocketHandler;
import io.nettix.websocket.WebSocketClientHandler;

import com.esotericsoftware.kryo.Kryo;

/**
 * Message queue client. Receives messages from the server.
 *
 * @author sanha
 *
 * @param <E>
 *          message type
 */
public class MessageSubscriber<E extends Enum<E>>
    extends PersistentClientChannelManager
{
  /**
   * Logger.
   */
  private static final Logger _logger = LoggerFactory.getLogger(MessageSubscriber.class);

  /**
   * URI used for WebSocket connection.
   */
  private final URI _uri;

  /**
   * Message encoder.
   */
  private final KryoWebSocketEncoder _encoder;

  /**
   * Message decoder.
   */
  private final KryoWebSocketDecoder _decoder;

  /**
   * Dispatcher for received messages.
   */
  private final Dispatcher _dispatcher = new Dispatcher();

  /**
   * Message listener map. Key is the message event type ID.
   */
  private final Map<Integer, MessageListener<?, ?>> _map = new HashMap<Integer, MessageListener<?, ?>>();

  /**
   * Message event type constants.
   */
  private E[] _types;

  /**
   * Connection check message interval (seconds).
   */
  private final int _enquireLinkDelay;

  /**
   * Message response timeout (seconds).
   */
  private final int _resTimeout;

  /**
   * Connection handler.
   */
  private ChannelHandler _connectionHandler;

  /**
   * Dispatcher that forwards received messages to listeners.
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  @Sharable
  private class Dispatcher
      extends InboundMessageHandler
  {
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
        throws Exception
    {
      Channel ch = ctx.getChannel();
      Message msg = (Message) e.getMessage();
      E type = _types[msg.type];

      if (msg.seq != null)
        _logger.debug("Received message [{}] <{}> {}", msg.seq, type, msg.value);
      else
        _logger.debug("Received message <{}> {}", type, msg.value);

      MessageListener listener = _map.get(msg.type);

      if (listener == null)
        throw new ListenerNotFoundException(type.name());

      Object ret = listener.messageReceived(ch, msg.value);

      if (msg.seq != null)
        {
          msg.value = ret;
          ch.write(msg);
        }
    }
  }

  /**
   * Constructor.
   *
   * @param name
   *          message queue name, used for logging
   * @param uri
   *          URI for server connection
   * @param factory
   *          Kryo object factory
   * @throws URISyntaxException
   */
  public MessageSubscriber(String name, String uri, PoolableKryoFactory factory)
      throws URISyntaxException
  {
    this(name, uri, factory, 60, 10);
  }

  /**
   * Constructor.
   *
   * @param name
   *          message queue name, used for logging
   * @param uri
   *          URI for server connection
   * @param factory
   *          Kryo object factory
   * @param delay
   *          connection check interval (seconds)
   * @param timeout
   *          connection check timeout (seconds)
   * @throws URISyntaxException
   */
  public MessageSubscriber(String name, String uri,
                           PoolableKryoFactory factory, int delay, int timeout)
      throws URISyntaxException
  {
    super(name);
    _uri = new URI(uri);
    ObjectPool<Kryo> pool = factory.getKryoPool();
    _encoder = new KryoWebSocketEncoder(pool);
    _decoder = new KryoWebSocketDecoder(pool, Message.class);
    _enquireLinkDelay = delay;
    _resTimeout = timeout;
  }

  /**
   * Sets the connection handler.
   *
   * @param handler
   *          the connection handler
   */
  public void setConnectionHandler(ConnectStateEventHandler handler)
  {
    _connectionHandler = handler;
  }

  @Override
  public ChannelPipeline getPipeline() throws Exception
  {
    ChannelPipeline p = super.getPipeline();
    p.addLast("HTTP_DECODER", new HttpResponseDecoder());
    p.addLast("HTTP_ENCODER", new HttpRequestEncoder());

    AbstractWebSocketHandler handler;

    if (_connectionHandler != null)
      handler = new WebSocketClientHandler(_uri, _connectionHandler, _encoder);
    else
      handler = new WebSocketClientHandler(_uri, _encoder);

    handler.setEnquireLink(name(), _enquireLinkDelay, _enquireLinkDelay
                                                      + _resTimeout);
    p.addLast("WEBSOCKET_CLIENT", handler);

    p.addLast("KRYO_DECODER", _decoder);
    p.addLast("MSG_DISPATCHER", _dispatcher);
    return p;
  }

  /**
   * Adds a message listener.
   *
   * @param type
   *          message type
   * @param listener
   *          the listener
   */
  @SuppressWarnings("unchecked")
  public void addListener(E type, MessageListener<?, ?> listener)
  {
    if (_types == null)
      _types = (E[]) type.getClass().getEnumConstants();

    if (_map.containsKey(type.ordinal()))
      throw new IllegalArgumentException(
                                         "Message listener was already added - "
                                             + _types[type.ordinal()]);
    else
      _map.put(type.ordinal(), listener);
  }

}
