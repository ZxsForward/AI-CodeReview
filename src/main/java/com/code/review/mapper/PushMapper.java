package com.code.review.mapper;

import com.code.review.entity.PushEventEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PushMapper {

    void insertPush(PushEventEntity entity);
}
