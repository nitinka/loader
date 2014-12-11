package com.flipkart.perf.server.health;

import com.codahale.metrics.health.HealthCheck;
import com.flipkart.perf.server.daemon.CounterCompoundThread;

/**
 * Created with IntelliJ IDEA.
 * User: nitinka
 * Date: 18/6/13
 * Time: 4:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class CounterCompoundThreadHealthCheck extends HealthCheck {

    @Override
    protected Result check() throws Exception {
        return CounterCompoundThread.instance().isAlive() ? Result.healthy("CounterCompoundThread is Alive") : Result.unhealthy("CounterCompoundThread is Dead");
    }
}
