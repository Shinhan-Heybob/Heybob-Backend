package com.shinhan.heybob.common.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class KSTUtil {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static String nowDateKst() {
        return LocalDateTime.now(KST).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    public static String nowTimeKst() {
        return LocalDateTime.now(KST).format(DateTimeFormatter.ofPattern("HHmmss"));
    }

    // yyyyMMdd + HHmmss + 6자리 난수
    public static String makeUniqueNo() {
        LocalDateTime now = LocalDateTime.now(KST);
        String ts = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String rnd = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        return ts + rnd;
    }
}
