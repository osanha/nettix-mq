

package io.nettix.mq.kryo;

import nf.fr.eraasoft.pool.ObjectPool;
import nf.fr.eraasoft.pool.PoolException;
import nf.fr.eraasoft.pool.PoolSettings;
import nf.fr.eraasoft.pool.PoolableObjectBase;
import io.nettix.mq.Message;

import com.esotericsoftware.kryo.Kryo;

/**
 * Abstract Kryo object pool factory.
 *
 * @author sanha
 */
public abstract class PoolableKryoFactory
    extends PoolableObjectBase<Kryo>
{
  /**
   * Kryo object pool.
   */
  private final ObjectPool<Kryo> _pool;

  /**
   * Constructor.
   */
  public PoolableKryoFactory()
  {
    _pool = new PoolSettings<Kryo>(this).pool();
  }

  /**
   * Constructor.
   *
   * @param min
   *          minimum pool size
   * @param max
   *          maximum pool size
   */
  public PoolableKryoFactory(int min, int max)
  {
    _pool = new PoolSettings<Kryo>(this).min(min).max(max).pool();
  }

  @Override
  public Kryo make()
  {
    Kryo kryo = new Kryo();
    kryo.setReferences(false);
    kryo.setRegistrationRequired(true);
    kryo.register(Message.class);
    initialize(kryo);
    return kryo;
  }

  @Override
  public void activate(Kryo t) throws PoolException
  {
  }

  /**
   * Returns the Kryo object pool.
   *
   * @return the object pool
   */
  public ObjectPool<Kryo> getKryoPool()
  {
    return _pool;
  }

  /**
   * Registers message types with Kryo. For performance, message types sent via
   * the message queue must be registered. Java native types such as String,
   * Integer, etc. are included by default and do not need to be registered.
   *
   * @param kryo
   *          the Kryo instance to register types with
   */
  protected abstract void initialize(Kryo kryo);

}
