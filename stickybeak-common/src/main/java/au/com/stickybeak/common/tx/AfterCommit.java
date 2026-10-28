package au.com.stickybeak.common.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 在当前事务提交后执行操作；没有活动事务时立即执行。
 * 用于远程调用、Redis 写入等无法随数据库回滚的副作用：先提交，再执行，
 * 避免"数据库回滚了，副作用却已经发生"。
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /**
     * 当前事务没有提交（回滚，或提交失败、结果未知）时执行补偿；没有活动事务时不做任何事。
     * 用于撤销已经提前做掉的副作用，例如 Redis 预扣。补偿动作必须幂等。
     * 提交结果未知时也执行：补偿方向应当是安全的（例如多还的 Redis 库存仍有 MySQL 条件更新兜底）。
     */
    public static void onRollback(Runnable compensation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    compensation.run();
                }
            }
        });
    }
}
