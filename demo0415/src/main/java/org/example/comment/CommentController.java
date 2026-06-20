package org.example.comment;

import org.example.auth.UserPrincipal;
import org.example.comment.dto.CommentResponse;
import org.example.comment.dto.CreateCommentRequest;
import org.example.common.ApiResponse;
import org.example.domain.Comment;
import org.example.domain.Post;
import org.example.domain.UserAction;
import org.example.repo.CommentRepository;
import org.example.repo.PostRepository;
import org.example.repo.UserActionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api")
public class CommentController {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserActionRepository userActionRepository;

    public CommentController(CommentRepository commentRepository,
                             PostRepository postRepository,
                             UserActionRepository userActionRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userActionRepository = userActionRepository;
    }

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<Page<CommentResponse>> list(@PathVariable Long postId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CommentResponse> out = commentRepository.findByPostId(postId, pageable).map(CommentController::toResp);
        return ApiResponse.ok(out);
    }

    @PostMapping("/posts/{postId}/comments")
    @Transactional
    public ApiResponse<CommentResponse> create(Authentication authentication,
                                               @PathVariable Long postId,
                                               @Valid @RequestBody CreateCommentRequest req) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Post post = postRepository.findById(postId).orElseThrow(() -> new IllegalArgumentException("帖子不存在"));

        Comment c = new Comment();
        c.setPostId(postId);
        c.setUserId(principal.getId());
        c.setParentId(req.getParentId());
        c.setContent(req.getContent());
        c.setLikeCount(0);
        c.setIsAudited((short) 1);
        c.setCreatedAt(LocalDateTime.now());
        Comment saved = commentRepository.save(c);

        post.setCommentCount((post.getCommentCount() == null ? 0 : post.getCommentCount()) + 1);
        postRepository.save(post);

        UserAction action = new UserAction();
        action.setUserId(principal.getId());
        action.setPostId(postId);
        action.setActionType("comment");
        action.setActionTime(LocalDateTime.now());
        userActionRepository.save(action);

        return ApiResponse.ok(toResp(saved));
    }

    private static CommentResponse toResp(Comment c) {
        CommentResponse r = new CommentResponse();
        r.setId(c.getId());
        r.setPostId(c.getPostId());
        r.setUserId(c.getUserId());
        r.setParentId(c.getParentId());
        r.setContent(c.getContent());
        r.setLikeCount(c.getLikeCount());
        r.setIsAudited(c.getIsAudited());
        r.setCreatedAt(c.getCreatedAt());
        return r;
    }
}

