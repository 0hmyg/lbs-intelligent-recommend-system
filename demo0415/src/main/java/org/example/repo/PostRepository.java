package org.example.repo;

import org.example.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("select p from Post p where p.userId = :userId and p.deletedAt is null order by p.createdAt desc")
    Page<Post> findMyActivePosts(@Param("userId") Long userId, Pageable pageable);

    @Query("select p from Post p where p.userId = :userId order by p.createdAt desc")
    Page<Post> findMyPosts(@Param("userId") Long userId, Pageable pageable);

    @Query("select p from Post p where p.userId = :userId and p.deletedAt is null and p.isAudited = :status order by p.createdAt desc")
    Page<Post> findMyPostsByStatus(@Param("userId") Long userId, @Param("status") short status, Pageable pageable);

    @Query("select p from Post p where p.userId = :userId and p.deletedAt is not null order by p.createdAt desc")
    Page<Post> findMyDeletedPosts(@Param("userId") Long userId, Pageable pageable);

    @Query("select p from Post p where p.deletedAt is null and p.isAudited = 0 order by p.createdAt desc")
    Page<Post> findPending(Pageable pageable);

    @Query("select p from Post p where p.deletedAt is null and p.isAudited = 1 and p.category = :category order by p.createdAt desc")
    Page<Post> findApprovedByCategory(@Param("category") String category, Pageable pageable);

    @Query("select p from Post p where p.deletedAt is null and p.isAudited = 1 order by p.createdAt desc")
    Page<Post> findApproved(Pageable pageable);

    Page<Post> findByIsAuditedAndDeletedAtIsNullOrderByCreatedAtDesc(short isAudited, Pageable pageable);

    @Query(
            value = "select p.id as id, ST_Distance(CAST(p.location_geom as geography), CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) as geography)) as distance_meters " +
                    "from posts p " +
                    "where p.deleted_at is null and p.is_audited = 1 and p.location_geom is not null " +
                    "order by distance_meters asc, p.created_at desc",
            countQuery = "select count(1) from posts p " +
                    "where p.deleted_at is null and p.is_audited = 1 and p.location_geom is not null",
            nativeQuery = true
    )
    List<Object[]> findNearbyRows(@Param("lng") double lng, @Param("lat") double lat);

    @Query(
            value = "select p.id as id, ST_Distance(CAST(p.location_geom as geography), CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) as geography)) as distance_meters " +
                    "from posts p " +
                    "where p.deleted_at is null and p.is_audited = 1 and p.location_geom is not null " +
                    "order by (coalesce(p.like_count,0) * 3 + coalesce(p.comment_count,0) * 5 + coalesce(p.view_count,0) * 0.2) desc, distance_meters asc, p.created_at desc",
            countQuery = "select count(1) from posts p " +
                    "where p.deleted_at is null and p.is_audited = 1 and p.location_geom is not null",
            nativeQuery = true
    )
    List<Object[]> findNearbyHotRows(@Param("lng") double lng, @Param("lat") double lat);
}

