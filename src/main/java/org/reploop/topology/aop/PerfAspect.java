package org.reploop.topology.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.util.StopWatch;

@Aspect
@Slf4j
public class PerfAspect {
    @Around(argNames = "jp", value = "execution(* org.reploop.topology.parser.LsofDriverDelegate.*(..))")
    public Object perf(ProceedingJoinPoint jp) throws Throwable {
        StopWatch watch = new StopWatch(jp.toShortString());
        try {
            return jp.proceed();
        } finally {
            watch.stop();
            log.info("{}", watch);
        }
    }
}
