package com.foodly.community.domain.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewCommentTest {

    @Test
    void absenceIsRepresentedAsEmptyContent() {
        assertThat(new ReviewComment(null)).isEqualTo(new ReviewComment(""));
        assertThat(new ReviewComment(null).value()).isEmpty();
    }

    @Test
    void doesNotRequireNonBlankContent() {
        assertThat(new ReviewComment("   ").value()).isEqualTo("   ");
    }

    @Test
    void preservesContentWithoutTrimmingOrArbitraryLengthLimit() {
        String content = "  Sabroso: ají y limón\n".repeat(2_000);
        assertThat(new ReviewComment(content).value()).isEqualTo(content);
    }

    @Test
    void equalityAndHashCodeDependOnExactContent() {
        var comment = new ReviewComment("Buen sabor");
        assertThat(comment).isEqualTo(new ReviewComment("Buen sabor"))
                .hasSameHashCodeAs(new ReviewComment("Buen sabor"));
        assertThat(comment).isNotEqualTo(new ReviewComment("Buen sabor "));
    }

    @Test
    void isImmutable() {
        assertThat(Modifier.isFinal(ReviewComment.class.getModifiers())).isTrue();
        assertThat(ReviewComment.class.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers()));
    }
}
