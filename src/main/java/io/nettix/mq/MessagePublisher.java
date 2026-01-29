

package io.nettix.mq;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nf.fr.eraasoft.pool.ObjectPool;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.ChannelGroupFutureListener;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroupFuture;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.timeout.ReadTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nettix.channel.CallableChannelFuture;
import io.nettix.channel.ServerChannelManager;
import io.nettix.channel.handler.ChannelReadTimeoutHandler;
import io.nettix.channel.handler.ConnectStateEventHandler;
import io.nettix.channel.handler.InboundMessageHandler;
import io.nettix.mq.kryo.KryoWebSocketDecoder;
import io.nettix.mq.kryo.KryoWebSocketEncoder;
import io.nettix.mq.kryo.PoolableKryoFactory;
import io.nettix.util.RoundRobinInteger;
import io.nettix.util.Singleton;
import io.nettix.util.TimeoutableMap;
import io.nettix.util.TimeoutableMap.TimeoutHandler;
import io.nettix.websocket.AbstractWebSocketHandler;
import io.nettix.websocket.WebSocketServerHandler;

import com.esotericsoftware.kryo.Kryo;

/**
 * Message publisher. Sends messages to connected subscribers using fire-and-forget or request-response patterns.
 *
 * @author sanha
 *
 * @param <E>
 *          message type
 */
public class MessagePublisher<E extends Enum<E>>
    extends ServerChannelManager
{
  /**
   * Logger.
   */
  private static final Logger _logger = LoggerFactory.getLogger(MessagePublisher.class);

  /**
   * Empty channel group future used when no subscribers are connected.
   */
  private static final ChannelGroupFuture _emptyGrpFuture = new DefaultChannelGroupFuture(
                                                                                          new DefaultChannelGroup(),
                                                                                          Collections.<ChannelFuture> emptyList());

  /**
   * Message receive timeout handler.
   */
  private final ChannelReadTimeoutHandler _timeoutHandler;

  /**
   * Channel check message interval (seconds).
   */
  private int _enquireLinkDelay = 60;

  /**
   * Response receive timeout (seconds).
   */
  private int _resTimeout = 10;

  /**
   * Message encoder.
   */
  private final KryoWebSocketEncoder _encoder;

  /**
   * Message decoder.
   */
  private final KryoWebSocketDecoder _decoder;

  /**
   * URI used for WebSocket connection.
   */
  private final URI _uri;

  /**
   * Handler for processing responses in request-response messaging.
   */
  private final Synchronizer _synchronizer = new Synchronizer();

  /**
   * Connection handler.
   */
  private ChannelHandler _connectionHandler;

  /**
   * Sequence generator for request-response messages to single subscriber.
   */
  private final RoundRobinInteger _singleSequencer = new RoundRobinInteger(
                                                                           1,
                                                                           RoundRobinInteger.MAX_POSITIVE_VALUE);

  /**
   * Sequence generator for request-response messages to multiple subscribers.
   */
  private final RoundRobinInteger _multiSequencer = new RoundRobinInteger(
                                                                          RoundRobinInteger.MAX_NEGATIVE_VALUE,
                                                                          -1);

  /**
   * Map for handling request-response messaging.
   */
  private final TimeoutableMap<Integer, Object> _futureMap;

  /**
   * Message type constants.
   */
  private E[] _types;

  /**
   * Channel handler that processes responses for request-response messaging.
   */
  @Sharable
  private class Synchronizer
      extends InboundMessageHandler
  {
    @SuppressWarnings("unchecked")
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
        throws Exception
    {
      Object future;
      Message msg = (Message) e.getMessage();

      if (msg.seq > 0)
        future = _futureMap.remove(msg.seq);
      else
        future = _futureMap.get(msg.seq);

      _logger.debug("Received response [{}] <{}> {}", msg.seq,
                    _types[msg.type], msg.value);

      if (future != null)
        {
          if (future instanceof ChannelFuture)
            {
              ((CallableChannelFuture<Object>) future).setSuccess(msg.value);
            }
          else
            {
              ChannelGroupFuture grpFuture = (ChannelGroupFuture) future;
              ChannelFuture f = grpFuture.find(ctx.getChannel());

              if (f != null)
                {
                  ((CallableChannelFuture<Object>) f).setSuccess(msg.value);

                  if ((msg.seq < 0) && grpFuture.isDone())
                    _futureMap.remove(msg.seq);
                }
            }
        }
    }
  }

  /**
   * Constructor.
   *
   * @param name
   *          message publisher name, used for logging
   * @param uri
   *          URI for WebSocket connection
   * @param factory
   *          Kryo pool factory
   * @param port
   *          listening port
   * @throws URISyntaxException
   */
  public MessagePublisher(String name, String uri,
                          PoolableKryoFactory factory, int port)
      throws URISyntaxException
  {
    this(name, uri, factory, port, 60, 10);
  }

  /**
   * Constructor.
   *
   * @param name
   *          message publisher name, used for logging
   * @param uri
   *          URI for WebSocket connection
   * @param factory
   *          Kryo pool factory
   * @param port
   *          listening port
   * @param delay
   *          enquire link message interval (seconds)
   * @param timeout
   *          response timeout for request-response messaging (seconds)
   * @throws URISyntaxException
   */
  public MessagePublisher(String name, String uri,
                          PoolableKryoFactory factory, int port, int delay,
                          int timeout) throws URISyntaxException
  {
    super(name, port);
    useChannelGroup(true);
    _uri = new URI(uri);
    _enquireLinkDelay = delay;
    _resTimeout = timeout;
    _timeoutHandler = new ChannelReadTimeoutHandler(Singleton.Timer,
                                                    _enquireLinkDelay
                                                        + _resTimeout);
    ObjectPool<Kryo> pool = factory.getKryoPool();
    _encoder = new KryoWebSocketEncoder(pool);
    _decoder = new KryoWebSocketDecoder(pool, Message.class);
    _futureMap = new TimeoutableMap<Integer, Object>("message queue", timeout);

    _futureMap.setTimeoutHandler(new TimeoutHandler<Integer, Object>()
    {
      @Override
      public void handleTimeout(Integer seq, Object future)
      {
        if (future instanceof ChannelFuture)
          {
            ((ChannelFuture) future).setFailure(new ReadTimeoutException());
          }
        else
          {
            for (ChannelFuture f : (ChannelGroupFuture) future)
              {
                if (!f.isDone())
                  f.setFailure(new ReadTimeoutException());
              }
          }
      }
    });
  }

  /**
   * Broadcasts a message to all subscribers (fire-and-forget, no response).
   *
   * @param type
   *          message type
   * @param value
   *          the message
   * @return the result future
   */
  @SuppressWarnings("unchecked")
  public ChannelGroupFuture publish(E type, Object value)
  {
    if (_types == null)
      _types = (E[]) type.getClass().getEnumConstants();

    ChannelGroup group = connections();

    if (group.size() == 0)
      return _emptyGrpFuture;

    _logger.debug("Broadcast message <{}> {}", type, value);
    return group.write(new Message(type.ordinal(), value));
  }

  /**
   * Sends a message to a specific subscriber (fire-and-forget, no response).
   *
   * @param ch
   *          target channel
   * @param type
   *          message type
   * @param value
   *          the message
   * @return the result future
   */
  @SuppressWarnings("unchecked")
  public ChannelFuture publish(Channel ch, E type, Object value)
  {
    if (_types == null)
      _types = (E[]) type.getClass().getEnumConstants();

    _logger.debug("Unicast message <{}> {}", type, value);
    return ch.write(new Message(type.ordinal(), value));
  }

  /**
   * Sends a message to a specific subscriber (request-response).
   *
   * @param ch
   *          target channel
   * @param type
   *          message type
   * @param value
   *          the message
   * @return the result future
   */
  @SuppressWarnings("unchecked")
  public CallableChannelFuture<Object> request(Channel ch, E type,
                                               Object value)
  {
    if (_types == null)
      _types = (E[]) type.getClass().getEnumConstants();

    final int seq = _singleSequencer.next();
    _logger.debug("Unicast request [{}] <{}> {}", seq, type, value);

    final CallableChannelFuture<Object> finalFuture = new CallableChannelFuture<Object>(
                                                                                        ch);
    _futureMap.put(seq, finalFuture);
    ChannelFuture ioFuture = ch.write(new Message(seq, type.ordinal(), value));

    ioFuture.addListener(new ChannelFutureListener()
    {
      @Override
      public void operationComplete(ChannelFuture ioFuture) throws Exception
      {
        if (!ioFuture.isSuccess())
          {
            finalFuture.setFailure(ioFuture.getCause());
            _futureMap.remove(seq);
          }
      }
    });

    return finalFuture;
  }

  /**
   * Sets the connection event handler.
   *
   * @param handler
   *          the handler
   */
  public void setConnectionHandler(ConnectStateEventHandler handler)
  {
    _connectionHandler = handler;
  }

  /**
   * Broadcasts a message to all subscribers (request-response).
   *
   * @param type
   *          message type
   * @param value
   *          the message
   * @return the result future reflecting response reception
   */
  @SuppressWarnings("unchecked")
  public ChannelGroupFuture request(E type, Object value)
  {
    if (_types == null)
      _types = (E[]) type.getClass().getEnumConstants();

    int tmpSeq;
    ChannelGroup group = connections();

    if (group.size() == 0)
      return _emptyGrpFuture;
    else if (group.size() == 1)
      tmpSeq = _singleSequencer.next();
    else
      tmpSeq = _multiSequencer.next();

    final int seq = tmpSeq;
    _logger.debug("Broadcast request [{}] <{}> {}", seq, type, value);

    List<ChannelFuture> futures = new ArrayList<ChannelFuture>(group.size());

    for (Channel ch : group)
      futures.add(new CallableChannelFuture<Object>(ch));

    final ChannelGroupFuture finalGf = new DefaultChannelGroupFuture(group,
                                                                     futures);
    _futureMap.put(seq, finalGf);
    ChannelGroupFuture ioGf = group.write(new Message(seq, type.ordinal(),
                                                      value));
    ioGf.addListener(new ChannelGroupFutureListener()
    {
      @Override
      public void operationComplete(ChannelGroupFuture ioGf) throws Exception
      {
        if (!ioGf.isCompleteSuccess())
          {
            _logger.error("Some message sending failed");

            for (ChannelFuture future : ioGf)
              {
                if (!future.isSuccess())
                  finalGf.find(future.getChannel()).setFailure(future.getCause());
              }

            if (finalGf.isDone())
              _futureMap.remove(seq);
          }
      }
    });

    return finalGf;
  }

  @Override
  public ChannelPipeline getPipeline() throws Exception
  {
    ChannelPipeline p = super.getPipeline();
    p.addLast("HTTP_DECODER", new HttpRequestDecoder());
    p.addLast("HTTP_ENCODER", new HttpResponseEncoder());
    p.addLast("CHANNEL_TIMEOUT", _timeoutHandler);

    AbstractWebSocketHandler handler;

    if (_connectionHandler != null)
      handler = new WebSocketServerHandler(_uri, _connectionHandler, _encoder);
    else
      handler = new WebSocketServerHandler(_uri, _encoder);

    p.addLast("WEBSOCKET_SERVER", handler);
    p.addLast("KRYO_DECODER", _decoder);
    p.addLast("MSG_SYNCHRONIZER", _synchronizer);

    return p;
  }

  @Override
  public void tearDown() throws Exception
  {
    super.tearDown();
    connections().close();
  }

}
