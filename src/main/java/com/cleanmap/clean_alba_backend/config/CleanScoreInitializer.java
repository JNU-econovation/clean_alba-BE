package com.cleanmap.clean_alba_backend.config;

import com.cleanmap.clean_alba_backend.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// 앱 기동 후(시드 데이터 로드 이후) 전체 사업장의 클린지수를 승인된 리뷰 기준으로 계산한다.
// 하드코딩 없이 계산 로직으로 점수가 채워지도록 보장한다.
@Component
@RequiredArgsConstructor
public class CleanScoreInitializer implements ApplicationRunner {

    private final WorkspaceService workspaceService;

    @Override
    public void run(ApplicationArguments args) {
        workspaceService.recalculateAllCleanScores();
    }
}
