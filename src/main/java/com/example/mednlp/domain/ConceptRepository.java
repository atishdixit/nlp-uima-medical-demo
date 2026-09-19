package com.example.mednlp.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConceptRepository extends JpaRepository<Concept, Long> {

    /** Loads concepts together with their terms in one query (no N+1). */
    @Query("select distinct c from Concept c left join fetch c.terms")
    List<Concept> findAllWithTerms();
}
