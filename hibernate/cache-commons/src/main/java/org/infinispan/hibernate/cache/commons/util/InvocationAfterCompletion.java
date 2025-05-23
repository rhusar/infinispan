package org.infinispan.hibernate.cache.commons.util;

import org.hibernate.HibernateException;
import org.infinispan.hibernate.cache.commons.access.SessionAccess.TransactionCoordinatorAccess;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class InvocationAfterCompletion implements Synchronization {
	protected static final InfinispanMessageLogger log = InfinispanMessageLogger.Provider.getLog( InvocationAfterCompletion.class );

	protected final TransactionCoordinatorAccess tc;
	protected final boolean requiresTransaction;

   public InvocationAfterCompletion(TransactionCoordinatorAccess tc, boolean requiresTransaction) {
		this.tc = tc;
		this.requiresTransaction = requiresTransaction;
	}

	@Override
	public void beforeCompletion() {
	}

	@Override
	public void afterCompletion(int status) {
		switch (status) {
			case Status.STATUS_COMMITTING:
			case Status.STATUS_COMMITTED:
				invokeIsolated(true);
				break;
			default:
				// it would be nicer to react only on ROLLING_BACK and ROLLED_BACK statuses
				// but TransactionCoordinator gives us UNKNOWN on rollback
				invokeIsolated(false);
				break;
		}
	}

	protected void invokeIsolated(final boolean success) {
		try {
			tc.delegateWork((executor, connection) -> {
				invoke(success);
				return null;
			}, requiresTransaction);
		}
		catch (HibernateException e) {
			// silently fail any exceptions
			if (log.isTraceEnabled()) {
				log.trace("Exception during query cache update", e);
			}
		}
	}

	protected abstract void invoke(boolean success);
}
