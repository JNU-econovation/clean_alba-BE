package com.cleanmap.clean_alba_backend.util;

import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 로그아웃된 토큰들을 저장하는 집합
// 서버 메모리에 저장하므로 서버 재시작 시 초기화됨
@Component
public class JwtBlacklistUtill {

    // 로그아웃된 토큰들을 저장하는 집합
    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();

    // 토큰을 블랙리스트에 추가(로그아웃 시 호출)
    public void addToBlacklist(String token){
        blacklist.add(token);
    }

    //토큰이 블랙리스트에 있는지 확인 (요청마다 확인)
    public boolean isBlacklisted(String token){
        return blacklist.contains(token);
    }
}
