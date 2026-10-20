package rw.gov.ptc.committee;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Runs work only once the surrounding transaction has committed. */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable work) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    work.run();
                }
            });
        } else {
            work.run();
        }
    }
}
