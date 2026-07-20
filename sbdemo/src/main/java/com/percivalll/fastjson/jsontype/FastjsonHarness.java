package com.percivalll.fastjson.jsontype;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.ParserConfig;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class FastjsonHarness {
    public Map<String, Object> parse(String lane, String payload) {
        Map<String, Object> result = base(lane);
        try {
            Object parsed = JSON.parse(payload);
            result.put("ok", Boolean.TRUE);
            result.put("resultClass", parsed == null ? "null" : parsed.getClass().getName());
            result.put("result", String.valueOf(parsed));
        } catch (Throwable throwable) {
            result.put("ok", Boolean.FALSE);
            result.put("errorType", throwable.getClass().getName());
            result.put("error", String.valueOf(throwable));
        }
        return result;
    }

    public Map<String, Object> info(String lane) {
        return base(lane);
    }

    private Map<String, Object> base(String lane) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        Thread thread = Thread.currentThread();
        result.put("lane", lane);
        result.put("thread", thread.getName());
        result.put("threadContextClassLoader", String.valueOf(thread.getContextClassLoader()));
        result.put("parserConfigClassLoader", String.valueOf(ParserConfig.class.getClassLoader()));
        result.put("autoTypeSupport", ParserConfig.getGlobalInstance().isAutoTypeSupport());
        return result;
    }
}
