package com.cleanmap.clean_alba_backend.config;

import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.ReviewSentiment;
import com.cleanmap.clean_alba_backend.domain.ReviewStatus;
import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.dto.ReviewCreateRequest;
import com.cleanmap.clean_alba_backend.repository.ReviewRepository;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import com.cleanmap.clean_alba_backend.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed.real-data", name = "enabled", havingValue = "true")
public class RealDataSeeder implements ApplicationRunner {

    private static final List<SeedWorkspace> WORKSPACES = List.of(
            new SeedWorkspace(
                    "탑독PC", "전남광주통합특별시 북구 우치로 128 3층", "PC방", "전대후문",
                    "35.1788460", "126.9123310",
                    List.of(new SeedReview("seed:real-data:topdog:1", new ReviewCreateRequest(
                            false, false, false, false, false, false, false, false, 3,
                            "근무 환경이 전반적으로 안정적이었습니다.")))),
            new SeedWorkspace(
                    "디저트39 전대점", "전남광주통합특별시 북구 호동로 12-8 1 2층", "카페", "전대후문",
                    "35.1744670", "126.9142980",
                    List.of(
                            new SeedReview("seed:real-data:dessert39:1", new ReviewCreateRequest(
                                    false, false, false, false, false, false, false, false, 2,
                                    "업무 안내가 명확하고 동료들과 협업하기 좋았습니다.")),
                            new SeedReview("seed:real-data:dessert39:2", new ReviewCreateRequest(
                                    false, false, false, true, false, false, false, false, 2,
                                    "바쁜 시간에는 휴게시간 조율이 필요했습니다.")))),
            new SeedWorkspace(
                    "더벤티 전남대", "전남광주통합특별시 북구 우치로 120 1층", "카페", "전대후문",
                    "35.1782980", "126.9123570",
                    List.of(
                            new SeedReview("seed:real-data:theventi:1", new ReviewCreateRequest(
                                    false, false, false, false, false, false, false, false, 2,
                                    "처음 근무할 때 업무 교육을 친절하게 받았습니다.")),
                            new SeedReview("seed:real-data:theventi:2", new ReviewCreateRequest(
                                    false, false, false, false, false, true, false, false, 3,
                                    "일정 변경은 미리 공유되면 좋겠습니다.")),
                            new SeedReview("seed:real-data:theventi:3", new ReviewCreateRequest(
                                    false, false, false, false, true, false, false, false, 2,
                                    "급여 일정 안내를 조금 더 구체적으로 받으면 좋겠습니다.")),
                            new SeedReview("seed:real-data:theventi:4", new ReviewCreateRequest(
                                    false, false, false, false, false, false, false, false, 3,
                                    "매장 분위기가 차분하고 업무 분담이 잘 되어 있었습니다.")))),
            new SeedWorkspace(
                    "파스쿠찌 전남대점", "전남광주통합특별시 북구 호동로 15 1~4층", "카페", "전대후문",
                    "35.1751910", "126.9142310",
                    List.of(
                            new SeedReview("seed:real-data:pascucci:1", new ReviewCreateRequest(
                                    false, false, false, false, false, false, false, false, 4,
                                    "근무 절차와 담당 업무가 명확하게 안내되었습니다.")),
                            new SeedReview("seed:real-data:pascucci:2", new ReviewCreateRequest(
                                    false, false, false, false, false, false, false, false, 3,
                                    "동료 간 소통이 원활해 업무를 배우기 좋았습니다.")),
                            new SeedReview("seed:real-data:pascucci:3", new ReviewCreateRequest(
                                    false, false, false, true, false, false, false, false, 3,
                                    "혼잡한 시간대 휴게시간 운영은 보완되면 좋겠습니다.")),
                            new SeedReview("seed:real-data:pascucci:4", new ReviewCreateRequest(
                                    false, false, false, false, false, true, false, false, 4,
                                    "스케줄 변경은 조금 더 일찍 공유되면 좋겠습니다.")),
                            new SeedReview("seed:real-data:pascucci:5", new ReviewCreateRequest(
                                    false, false, false, false, false, false, false, false, 3,
                                    "정리된 매뉴얼 덕분에 업무 적응이 수월했습니다.")),
                            new SeedReview("seed:real-data:pascucci:6", new ReviewCreateRequest(
                                    false, false, false, false, true, false, false, true, 3,
                                    "마감 시간 급여와 초과근무 안내가 더 명확하면 좋겠습니다."))))
    );

    private final WorkspaceRepository workspaceRepository;
    private final ReviewRepository reviewRepository;
    private final WorkspaceService workspaceService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (SeedWorkspace seedWorkspace : WORKSPACES) {
            Workspace workspace = workspaceRepository.findByNameAndAddress(seedWorkspace.name(), seedWorkspace.address())
                    .orElseGet(() -> workspaceRepository.save(new Workspace(
                            seedWorkspace.name(), seedWorkspace.address(), seedWorkspace.category(), seedWorkspace.district(),
                            new BigDecimal(seedWorkspace.latitude()), new BigDecimal(seedWorkspace.longitude()))));
            for (SeedReview seedReview : seedWorkspace.reviews()) {
                if (!reviewRepository.existsByWorkspace_WorkspaceIdAndAuthorEmail(
                        workspace.getWorkspaceId(), seedReview.authorKey())) {
                    Review review = new Review(workspace, seedReview.request(), seedReview.authorKey());
                    review.updateSentiment(seedSentiment(seedReview.authorKey()));
                    review.moderate(ReviewStatus.APPROVED);
                    reviewRepository.save(review);
                }
            }
            workspaceService.recalculateCleanScore(workspace.getWorkspaceId());
        }
    }

    private record SeedWorkspace(
            String name,
            String address,
            String category,
            String district,
            String latitude,
            String longitude,
            List<SeedReview> reviews
    ) {
    }

    private record SeedReview(String authorKey, ReviewCreateRequest request) {
    }

    private static ReviewSentiment seedSentiment(String authorKey) {
        return switch (authorKey) {
            case "seed:real-data:topdog:1",
                 "seed:real-data:dessert39:1",
                 "seed:real-data:theventi:1",
                 "seed:real-data:theventi:4",
                 "seed:real-data:pascucci:1",
                 "seed:real-data:pascucci:2",
                 "seed:real-data:pascucci:5" -> ReviewSentiment.POSITIVE;
            case "seed:real-data:dessert39:2",
                 "seed:real-data:theventi:2" -> ReviewSentiment.NEUTRAL;
            case "seed:real-data:theventi:3",
                 "seed:real-data:pascucci:3",
                 "seed:real-data:pascucci:4",
                 "seed:real-data:pascucci:6" -> ReviewSentiment.NEGATIVE;
            default -> throw new IllegalArgumentException("Unknown seed review author key: " + authorKey);
        };
    }
}
