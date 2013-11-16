package com.triompha.cache.impl;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.SerializationUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.triompha.cache.Cache;
import com.triompha.cache.exception.CacheException;

import redis.clients.jedis.ShardedJedis;
import redis.clients.jedis.ShardedJedisPool;


public class ShardedRedisCacheImpl implements Cache{

    public static final Logger logger = Logger.getLogger(ShardedRedisCacheImpl.class);

    public static final String WARNINFO = "cache key or obj is null";

    private ShardedJedisPool  jedisPool;
    public void setShardedJedis(ShardedJedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(String key) {
        ShardedJedis jedis = null;
        try {
            if(StringUtils.isEmpty(key)){
                throw new CacheException(WARNINFO);
            }
            jedis = jedisPool.getResource();
            byte[] obj = jedis.get(key.getBytes());
            if(obj!=null)  return  (T) SerializationUtils.deserialize(obj);
        } catch (CacheException e) {
            logger.warn("invoke put method of (null key or null value) key:"+key , e);
        } catch (ClassCastException e) {
            logger.error("error of class cast when get object from redis , the key is "+key , e);
        } catch (Exception e) {
            logger.error("error when get object from redis , the key is "+key , e);
            jedisPool.returnBrokenResource(jedis);
        } finally {
            if (null != jedisPool) {
                jedisPool.returnResource(jedis);
            }
        }
        return null;
    }

    @Override
    public void put(String key, Serializable value) {
        ShardedJedis jedis = null;
        String rst = null;
        try {
            //如果key或value为空则直接抛出异常，并且不进行任何存储
            if(StringUtils.isEmpty(key)||value==null){
                throw new CacheException(WARNINFO);
            }
            jedis = jedisPool.getResource();
            rst = jedis.set(key.getBytes(), SerializationUtils.serialize(value));
        }catch (CacheException e) {
            logger.warn("invoke put method of (null key or null value) key:"+key+" value:"+value ,e);
        }catch (Exception e) {
            logger.error("error when put object into redis , the key:value is "+key+":" + value , e);
            jedisPool.returnBrokenResource(jedis);
        } finally {
            if (null != jedisPool) {
                jedisPool.returnResource(jedis);
            }
        }
        logger.debug("invoke put method of key:"+key+" status:"+rst);

    }

    @Override
    public void remove(String key) {
        ShardedJedis jedis = null;
        Long rst = null;
        try {
            if(StringUtils.isEmpty(key)){
                throw new CacheException("WARNINFO");
            }
            jedis = jedisPool.getResource();
            rst = jedis.del(key);
        }catch (CacheException e) {
            logger.warn("invoke remove method of (null key) key:"+key , e);
        } catch (Exception e) {
            logger.error("error when remove object into redis , the key is "+key,e);
            jedisPool.returnBrokenResource(jedis);
        } finally {
            if (null != jedisPool) {
                jedisPool.returnResource(jedis);
            }
        }
        logger.debug("invoke remove method of key:"+key+" status:"+rst);
    }

    @Override
    public void put(String key, Serializable value, int ttl) {
        ShardedJedis jedis = null;
        String rest = null;
        try {
            if(StringUtils.isEmpty(key) || value==null){
                throw new CacheException(WARNINFO);
            }
            jedis = jedisPool.getResource();
            rest = jedis.setex(key.getBytes(), ttl, SerializationUtils.serialize(value));
        } catch (CacheException e) {
            logger.warn("invoke put method of (null key or null value) key:"+key+" value:"+value+" ttl:"+ttl ,e);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error when get object from redis , the key is "+key,e);
            jedisPool.returnBrokenResource(jedis);
        } finally {
            if (null != jedisPool) {
                jedisPool.returnResource(jedis);
            }
        }
        logger.debug("invoke remove method of key:"+key+" status:"+rest);

    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> List<T> getMulti(List<String> keys) {
        ShardedJedis jedis = null;
        try {
            if(CollectionUtils.isEmpty(keys)){
                throw new CacheException(WARNINFO);
            }
            List<T> result = new  ArrayList<T>();
            for(String key : keys){
                result.add((T) get(key));
            }
            return result;
        }  catch (CacheException e) {
            logger.warn("invoke getMulti method of (null key)" ,e);
        }  catch (Exception e) {
            logger.error("error when getMulti object from redis , the keys is "+keys,e);
            jedisPool.returnBrokenResource(jedis);
        } finally {
            if (null != jedisPool) {
                jedisPool.returnResource(jedis);
            }
        }
        return null;
    }


    public static void main(String[] args) throws MalformedURLException, RemoteException, NotBoundException {

        System.out.println(ShardedRedisCacheImpl.class);

        ApplicationContext app = new ClassPathXmlApplicationContext("/context/applicationContext-cache.xml");
        System.out.println(ShardedRedisCacheImpl.class);
        Cache cache = (Cache) app.getBean("redisCache");
        //        EntryService entryService = (EntryService) Naming.lookup("//10.10.77.52:8862/EntryRMIService");
        //        Entry entry = entryService.getEntryById(46205L,10004824L );
        //        for(int i=0 ;i<20;i++){
        //            cache.put("Entry_"+i, entry);
        //        }
        //
        //        System.out.println(entry);
        List<String> keys = new ArrayList<String>();

        for(int i=0 ;i<20;i++){
            keys.add("Entry_"+i);
        }
        for(int i = 0 ; i<10 ; i ++){
            long s1 =System.nanoTime();
            cache.getMulti(keys);
            System.out.println("muliti :" + (System.nanoTime()-s1));
        }
        for(int i = 0 ; i<10 ; i ++){
            long s2 = System.nanoTime();
            cache.get("Entry_0");
            System.out.println("single :" + (System.nanoTime()-s2));
        }
        for(int i = 0 ; i<10 ; i ++){
            long s3 = System.nanoTime();
            cache.getMulti(keys.subList(0, 10));
            System.out.println("maliti :" + (System.nanoTime()-s3));
        }

    }


}
