package tr.edu.etu.endofday.order;

/**
 * An order with the actions the customer can take on it now, so that clients do not have to repeat the rules.
 *
 * @param cancellable the order is confirmed and its pickup window has not started
 * @param reviewable the order is delivered and has no review yet
 */
public record OrderResult(OrderView order, boolean cancellable, boolean reviewable) {}
