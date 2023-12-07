package com.comments.utils;

/**
 * <p>
 *  分布式锁
 * </p>
 *
 * @author Colin
 * @since 2023/12/6
 */
public interface ILock {
    boolean setLock(long timeoutminute);
    void unLock();
}
