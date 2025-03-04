package com.cookiee.cookieeserver.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.cookiee.cookieeserver.controller.S3Uploader;
import com.cookiee.cookieeserver.domain.Category;
import com.cookiee.cookieeserver.domain.Event;
import com.cookiee.cookieeserver.domain.EventCategory;
import com.cookiee.cookieeserver.domain.User;
import com.cookiee.cookieeserver.dto.request.EventGetRequestDto;
import com.cookiee.cookieeserver.dto.request.EventRegisterRequestDto;
import com.cookiee.cookieeserver.dto.request.EventUpdateRequestDto;
import com.cookiee.cookieeserver.dto.response.EventResponseDto;
import com.cookiee.cookieeserver.repository.CategoryRepository;
import com.cookiee.cookieeserver.repository.EventCategoryRepository;
import com.cookiee.cookieeserver.repository.EventRepository;
import com.cookiee.cookieeserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class EventService  {
    @Autowired
    private final EventRepository eventRepository;
    @Autowired
    private final UserRepository userRepository;
    @Autowired
    private final CategoryRepository categoryRepository;
    @Autowired
    private final EventCategoryRepository eventCategoryRepository;
    @Autowired
    private S3Uploader s3Uploader;
    @Autowired
    private final AmazonS3 amazonS3Client;
    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;


    @Transactional
    public EventResponseDto createEvent(List<MultipartFile> images, EventRegisterRequestDto eventRegisterRequestDto, Long userId) throws IOException {
        User user = userRepository.findByUserId(userId).orElseThrow(
                () -> new IllegalArgumentException("해당 id의 사용자가 없습니다.")
        );


        List<Category> categoryList = eventRegisterRequestDto.categoryIds().stream()
                .map(
                        id -> categoryRepository.findByCategoryId(id).orElseThrow(
                                () -> new IllegalArgumentException("해당 id의 카테고리 없습니다...")
                        )
                )
                .collect(Collectors.toList());

        if (!images.isEmpty()) {
            List<String> storedFileNames = new ArrayList<>();

            for (MultipartFile image : images) {
                String storedFileName = s3Uploader.saveFile(image, String.valueOf(userId), "event");
                storedFileNames.add(storedFileName);
                System.out.println(storedFileName);
            }
            Event savedEvent = eventRepository.save(eventRegisterRequestDto.toEntity(user, new ArrayList<EventCategory>(), storedFileNames));
            List<EventCategory> eventCategoryList = categoryList.stream()
                    .map(category ->
                            EventCategory.builder().event(savedEvent).category(category).build()
                    ).collect(Collectors.toList());

            eventCategoryRepository.saveAll(eventCategoryList);
            savedEvent.setEventCategories(eventCategoryList);
            return EventResponseDto.from(savedEvent);
        }

        throw new NullPointerException("사진이 없습니다.");
    }

    @Transactional
    public EventResponseDto getEventDetail(long userId, long eventId){
        Event event = eventRepository.findByUserUserIdAndEventId(userId, eventId);
        return EventResponseDto.from(event);
    }

    @Transactional
    public List<EventResponseDto> getEventList(long userId, EventGetRequestDto eventGetRequestDto){
        List<Event> events = eventRepository.findByUserUserIdAndEventYearAndEventMonthAndEventDate(userId, eventGetRequestDto.eventYear(), eventGetRequestDto.eventMonth(), eventGetRequestDto.eventDate());
        return events.stream()
                .map(EventResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventResponseDto updateEvent(long userId, long eventId, EventUpdateRequestDto eventUpdateRequestDto, List<MultipartFile> images) throws IOException {
        Event updatedEvent = eventRepository.findByUserUserIdAndEventId(userId, eventId);

        List<String> imageUrls = updatedEvent.getImageUrl();
        for (String imageUrl : imageUrls){
            String fileName = extractFileNameFromUrl(imageUrl);
            amazonS3Client.deleteObject(bucketName, fileName);
        }

        List<String> storedFileNames = new ArrayList<>();
        for (MultipartFile image : images) {
            String storedFileName = s3Uploader.saveFile(image, String.valueOf(userId), "event");
            storedFileNames.add(storedFileName);
        }

        eventCategoryRepository.deleteAll(updatedEvent.getEventCategories());
        List<Category> categoryList = eventUpdateRequestDto.categoryIds().stream()
                .map(
                        id -> categoryRepository.findByCategoryId(id).orElseThrow(
                                () -> new IllegalArgumentException("해당 id의 카테고리 없습니다...")
                        )
                )
                .collect(Collectors.toList());
        List<EventCategory> eventCategoryList = categoryList.stream()
                .map(category ->
                        EventCategory.builder().event(updatedEvent).category(category).build()
                ).collect(Collectors.toList());

        eventCategoryRepository.saveAll(eventCategoryList);

        updatedEvent.update(
                eventUpdateRequestDto.eventWhat(),
                eventUpdateRequestDto.eventWhere(),
                eventUpdateRequestDto.withWho(),
                eventUpdateRequestDto.startTime(),
                eventUpdateRequestDto.endTime(),
                storedFileNames,
                eventCategoryList
        );

        return EventResponseDto.from(updatedEvent);
    }


    @Transactional
    public void deleteEvent(long userId, long eventId){
        Event deletedevent;
        deletedevent = eventRepository.findByUserUserIdAndEventId(userId, eventId);
        List<String> imageUrls = deletedevent.getImageUrl();
        List<EventCategory> deleteEventCategories = eventCategoryRepository.findEventCategoriesByEventEventId(eventId);
        for (EventCategory deleteEventCategory : deleteEventCategories){
            eventCategoryRepository.delete(deleteEventCategory);
        }
        for (String imageUrl : imageUrls){
            String fileName = extractFileNameFromUrl(imageUrl);
            amazonS3Client.deleteObject(bucketName, fileName);
        }
        eventRepository.delete(deletedevent);

    }
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


}

