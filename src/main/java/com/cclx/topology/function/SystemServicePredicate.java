package com.cclx.topology.function;

import com.cclx.topology.Constants;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

@Component
public class SystemServicePredicate implements Predicate<String> {
    @Override
    public boolean test(String cmd) {
        // kthreadd
        return Constants.K_THREAD_D.equals(cmd);
    }
}
