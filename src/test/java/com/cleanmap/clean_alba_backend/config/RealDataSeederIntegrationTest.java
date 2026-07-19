package com.cleanmap.clean_alba_backend.config;

import com.cleanmap.clean_alba_backend.domain.Review;
import com.cleanmap.clean_alba_backend.domain.ReviewSentiment;
import com.cleanmap.clean_alba_backend.domain.ReviewStatus;
import com.cleanmap.clean_alba_backend.domain.Workspace;
import com.cleanmap.clean_alba_backend.repository.ReviewRepository;
import com.cleanmap.clean_alba_backend.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "app.seed.real-data.enabled=true"
})
class RealDataSeederIntegrationTest {

    @Autowired
    private RealDataSeeder realDataSeeder;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Test
    void seedsEachWorkspaceAndReviewOnlyOnceWhenEnabled() throws Exception {
        // Given: explicit seed activation has populated an otherwise empty database
        assertSeededReviewCounts();
        assertSeededReviewSentiments();

        // When: the same seed runner is invoked again
        realDataSeeder.run(new DefaultApplicationArguments(new String[0]));

        // Then: name-and-address lookup and stable seed author keys prevent duplicates
        assertSeededReviewCounts();
        assertSeededReviewSentiments();
    }

    private void assertSeededReviewCounts() {
        assertReviewCount("탑독PC", "전남광주통합특별시 북구 우치로 128 3층", 1);
        assertReviewCount("디저트39 전대점", "전남광주통합특별시 북구 호동로 12-8 1 2층", 2);
        assertReviewCount("더벤티 전남대", "전남광주통합특별시 북구 우치로 120 1층", 4);
        assertReviewCount("파스쿠찌 전남대점", "전남광주통합특별시 북구 호동로 15 1~4층", 6);
    }

    private void assertReviewCount(String name, String address, int expectedCount) {
        Workspace workspace = workspaceRepository.findByNameAndAddress(name, address).orElseThrow();
        long reviewCount = reviewRepository.findByWorkspace_WorkspaceIdAndStatus(
                workspace.getWorkspaceId(), ReviewStatus.APPROVED).size();
        assertEquals(expectedCount, reviewCount);
    }

    private void assertSeededReviewSentiments() {
        Map<String, ReviewSentiment> sentiments = workspaceRepository.findAll().stream()
                .flatMap(workspace -> reviewRepository.findByWorkspace_WorkspaceIdAndStatus(
                        workspace.getWorkspaceId(), ReviewStatus.APPROVED).stream())
                .collect(Collectors.toMap(Review::getAuthorEmail, Review::getSentiment));

        assertEquals(Map.ofEntries(
                Map.entry("seed:real-data:topdog:1", ReviewSentiment.POSITIVE),
                Map.entry("seed:real-data:dessert39:1", ReviewSentiment.POSITIVE),
                Map.entry("seed:real-data:dessert39:2", ReviewSentiment.NEUTRAL),
                Map.entry("seed:real-data:theventi:1", ReviewSentiment.POSITIVE),
                Map.entry("seed:real-data:theventi:2", ReviewSentiment.NEUTRAL),
                Map.entry("seed:real-data:theventi:3", ReviewSentiment.NEGATIVE),
                Map.entry("seed:real-data:theventi:4", ReviewSentiment.POSITIVE),
                Map.entry("seed:real-data:pascucci:1", ReviewSentiment.POSITIVE),
                Map.entry("seed:real-data:pascucci:2", ReviewSentiment.POSITIVE),
                Map.entry("seed:real-data:pascucci:3", ReviewSentiment.NEGATIVE),
                Map.entry("seed:real-data:pascucci:4", ReviewSentiment.NEGATIVE),
                Map.entry("seed:real-data:pascucci:5", ReviewSentiment.POSITIVE),
                Map.entry("seed:real-data:pascucci:6", ReviewSentiment.NEGATIVE)
        ), sentiments);
    }
}
