package rw.gov.ptc.notification;

/**
 * A delivery channel. Email is the guaranteed channel; push is an
 * enhancement. An SMS gateway can be added later as another implementation.
 */
public interface NotificationChannel {

    String name();

    /** True when failure of this channel must be recorded and retried. */
    boolean guaranteed();

    /**
     * @return true when at least one message was handed to the channel
     * @throws Exception on delivery failure
     */
    boolean deliver(Notification notification, Recipient recipient) throws Exception;
}
