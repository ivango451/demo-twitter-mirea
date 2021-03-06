package ru.fbsmo.news.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import ru.fbsmo.news.entity.Tag;
import ru.fbsmo.news.repository.PostRepository;
import ru.fbsmo.news.repository.TagRepository;
import ru.fbsmo.news.repository.UserRepository;
import ru.fbsmo.news.util.SecurityUtils;
import ru.fbsmo.news.dto.PostDto;
import ru.fbsmo.news.entity.Post;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@Transactional
public class PostServiceImpl implements PostService{

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;

    @Autowired
    public PostServiceImpl(PostRepository postRepository, UserRepository userRepository, TagRepository tagRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.tagRepository = tagRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Post> search(String key) {
        return postRepository.findByContentContainingIgnoreCaseOrderByDtCreatedDesc(key);
    }

    @Override
    public List<Post> findByUser(String username) {
        List<Post> posts = userRepository.findByUsername(username)
                .orElseThrow(NoSuchElementException::new)
                .getPosts();
        posts.size();
        return posts;
    }

    @Override
    public List<Post> findByTag(String username) {
        return postRepository.findByTagName(username);
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public long createPost(PostDto postDto) {
        Post post = new Post();
        post.setTitle(postDto.getTitle());
        post.setContent(postDto.getContent());
        post.setTags(parseTags(postDto.getTags()));
        post.setDtCreated(LocalDateTime.now());

        String username = SecurityUtils.getCurrentUserDetails().getUsername();
        post.setUser(userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username)));
        postRepository.save(post);
        return post.getPostId();
    }

    @Override
    public void checkAuthority(long postId) {
        SecurityUtils.checkAuthority(postRepository.findById(postId)
                .orElseThrow()
                .getUser().getUsername());
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public void update(PostDto postDto) {
        checkAuthority(postDto.getPostId());
        Post post = postRepository.findById(postDto.getPostId()).orElseThrow();

        if (!StringUtils.isEmpty(postDto.getTitle()))
            post.setTitle(postDto.getTitle());
        if (!StringUtils.isEmpty(postDto.getContent()))
            post.setContent(postDto.getContent());
        if (!StringUtils.isEmpty(postDto.getTags()))
            post.setTags(parseTags(postDto.getTags()));

        post.setDtUpdated(LocalDateTime.now());
        postRepository.save(post);
    }

    @Override
    public void delete(long postId) {
        String username = postRepository.findById(postId)
                .orElseThrow()
                .getUser().getUsername();

        if (!SecurityUtils.hasAuthority(username) && !SecurityUtils.hasRole("ADMIN")){
            throw new AccessDeniedException(SecurityUtils.ACCESS_DENIED);
        }
        postRepository.deleteById(postId);
    }

    @Override
    public Post findById(long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(NoSuchElementException::new);
        post.getTags().size();
        post.getComments().size();
        return post;
    }

    private List<Tag> parseTags(String tags) {
        if (tags == null)
            return Collections.emptyList();


        return Arrays.stream(tags.split(" "))
                .map(tagName -> tagRepository
                        .findByName(tagName)
                        .orElseGet(() -> tagRepository.save(new Tag(tagName))))
                .collect(Collectors.toList());
    }

}
