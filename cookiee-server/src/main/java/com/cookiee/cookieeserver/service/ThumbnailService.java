package com.cookiee.cookieeserver.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.cookiee.cookieeserver.controller.S3Uploader;
import com.cookiee.cookieeserver.domain.Thumbnail;
import com.cookiee.cookieeserver.domain.User;
import com.cookiee.cookieeserver.dto.request.ThumbnailRegisterRequestDto;
import com.cookiee.cookieeserver.dto.request.ThumbnailUpdateRequestDto;
import com.cookiee.cookieeserver.dto.response.ThumbnailResponseDto;
import com.cookiee.cookieeserver.repository.ThumbnailRepository;
import com.cookiee.cookieeserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.net.URI;

@Slf4j
@RequiredArgsConstructor
@Service
public class ThumbnailService {
    @Autowired
    private final UserRepository userRepository;
    @Autowired
    private final ThumbnailRepository thumbnailRepository;
    @Autowired
    private S3Uploader s3Uploader;
    private final AmazonS3 amazonS3Client;
    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;


    @Transactional
    public ThumbnailResponseDto createThumbnail(MultipartFile thumbnailUrl, ThumbnailRegisterRequestDto thumbnailRegisterRequestDto, Long userId) throws IOException {
        User user = userRepository.findByUserId(userId).orElseThrow(
                () -> new IllegalArgumentException("해당 id의 사용자가 없습니다.")
        );
        Thumbnail savedThumbnail;
        String storedFileName = null;
        if (!thumbnailUrl.isEmpty())
            storedFileName = s3Uploader.saveFile(thumbnailUrl, String.valueOf(userId), "thumbnail");
        savedThumbnail = thumbnailRepository.save(thumbnailRegisterRequestDto.toEntity(user, storedFileName));

        return new ThumbnailResponseDto(savedThumbnail.getThumbnailId(), savedThumbnail.getEventYear(), savedThumbnail.getEventMonth(), savedThumbnail.getEventDate(), savedThumbnail.getThumbnailUrl());
    }

    @Transactional
    public List<ThumbnailResponseDto> getThumbnail(long userId){
        List<Thumbnail> thumbnails = thumbnailRepository.findThumbnailsByUserUserId(userId);
        return thumbnails.stream()
                .map(ThumbnailResponseDto::from)
                .collect(Collectors.toList());

    }

    @Transactional
    public void deleteThumbnail(long userId, long thumbnailId){
        Thumbnail deletedthumbnail;
        deletedthumbnail = thumbnailRepository.findByUserUserIdAndThumbnailId(userId, thumbnailId);
        String fileName = extractFileNameFromUrl(deletedthumbnail.getThumbnailUrl());
        amazonS3Client.deleteObject(bucketName, fileName);
        thumbnailRepository.delete(deletedthumbnail);
    }
    // 이미지 URL 파일명 추출
    private static String extractFileNameFromUrl(String imageUrl) {
        try {
            URI uri = new URI(imageUrl);
            String path = uri.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Transactional
    public ThumbnailResponseDto updateThumbnail(MultipartFile thumbnailUrl, ThumbnailUpdateRequestDto thumbnailUpdateRequestDto, long userId, long thumbnailId) throws IOException {
        Thumbnail updatedthumbnail = thumbnailRepository.findByUserUserIdAndThumbnailId(userId, thumbnailId);
        String fileName = extractFileNameFromUrl(updatedthumbnail.getThumbnailUrl());
        amazonS3Client.deleteObject(bucketName, fileName); //버킷에서 사진 삭제
        String updatedFileName = s3Uploader.saveFile(thumbnailUrl, String.valueOf(userId), "thumbnail");
        updatedthumbnail.update(updatedFileName);
        return new ThumbnailResponseDto(updatedthumbnail.getThumbnailId(), updatedthumbnail.getEventYear(), updatedthumbnail.getEventMonth(), updatedthumbnail.getEventDate(), updatedthumbnail.getThumbnailUrl());
    }
}
