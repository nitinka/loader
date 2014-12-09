package com.flipkart.perf.server.health;

import com.codahale.metrics.health.HealthCheck;
import com.flipkart.perf.server.daemon.TimerComputationThread;

/**
 * Created with IntelliJ IDEA.
 * User: nitinka
 * Date: 18/6/13
 * Time: 4:47 PM
 * To change this template use File | Settings | File Templates.
 */
public class TimerComputationThreadHealthCheck extends HealthCheck {
    @Override
    protected Result check() throws Exception {
        return TimerComputationThread.instance().isAlive() ?
                Result.healthy("TimerComputationThread is Alive") :
                Result.unhealthy("TimerComputationThread is Dead");
    }
}
