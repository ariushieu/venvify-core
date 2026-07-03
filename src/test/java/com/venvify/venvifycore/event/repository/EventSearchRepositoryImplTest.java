package com.venvify.venvifycore.event.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tokenizer FULLTEXT + escape LIKE — logic thuần, không cần DB. */
class EventSearchRepositoryImplTest {

    // ---- toBooleanModeQuery ----

    @Test
    void booleanQuery_multiWord_prefixAndRequired() {
        assertThat(EventSearchRepositoryImpl.toBooleanModeQuery("lập trình web"))
                .isEqualTo("+lập* +trình* +web*");
    }

    @Test
    void booleanQuery_stripsBooleanOperators() {
        // User không inject được cú pháp boolean mode (-loại_trừ, "phrase", wildcard...).
        assertThat(EventSearchRepositoryImpl.toBooleanModeQuery("-spring \"boot+\" (java)"))
                .isEqualTo("+spring* +boot* +java*");
    }

    @Test
    void booleanQuery_dropsTokensBelowMinTokenSize() {
        // "ai" < innodb_ft_min_token_size(3) — giữ lại sẽ match rỗng làm 0 kết quả oan.
        assertThat(EventSearchRepositoryImpl.toBooleanModeQuery("ai agent"))
                .isEqualTo("+agent*");
    }

    @Test
    void booleanQuery_allTokensTooShort_returnsNullForLikeFallback() {
        assertThat(EventSearchRepositoryImpl.toBooleanModeQuery("ai ml")).isNull();
    }

    @Test
    void booleanQuery_operatorOnlyInput_returnsNull() {
        assertThat(EventSearchRepositoryImpl.toBooleanModeQuery("+-*\"")).isNull();
    }

    // ---- escapeLike ----

    @Test
    void escapeLike_neutralizesWildcards() {
        assertThat(EventSearchRepositoryImpl.escapeLike("50%_off\\x"))
                .isEqualTo("50\\%\\_off\\\\x");
    }
}
