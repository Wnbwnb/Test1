package com.yunzhijia.k3cloud.dao.impl;

import com.mongodb.client.result.UpdateResult;
import com.yunzhijia.cds.pojo.vo.PageInfoVO;
import com.yunzhijia.k3cloud.dao.ErpCustomFieldDao;
import com.yunzhijia.k3cloud.objects.dto.ErpCustomFieldDto;
import com.yunzhijia.k3cloud.objects.entity.ErpCustomFieldEntity;
import org.apache.commons.lang.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ErpCustomFieldDaoImpl extends K3cloudBaseDaoImpl<ErpCustomFieldEntity> implements ErpCustomFieldDao {

    /**
    根据条件查全部
     */
    @Override
    public List<ErpCustomFieldEntity> queryAllByDto(ErpCustomFieldDto erpCustomFieldDto) {
        if(erpCustomFieldDto == null || StringUtils.isEmpty(erpCustomFieldDto.getEid())){
            return null;
        }
        Criteria criteria = baseQueryCondition(erpCustomFieldDto.getEid());
        if(StringUtils.isNotEmpty(erpCustomFieldDto.getFromSystem())){
            criteria.and("fromSystem").is(erpCustomFieldDto.getFromSystem());
        }
        if(StringUtils.isNotEmpty(erpCustomFieldDto.getInterfaceType())){
            criteria.and("interfaceType").is(erpCustomFieldDto.getInterfaceType());
        }
        if(StringUtils.isNotEmpty(erpCustomFieldDto.getInterfaceEngName())){
            criteria.and("interfaceEngName").is(erpCustomFieldDto.getInterfaceEngName());
        }

        Query query = new Query();
        query.addCriteria(criteria);
        query.with(new Sort(Sort.Direction.DESC, "createdTime"));
        List<ErpCustomFieldEntity> list =  mongoTemplate.find(query, ErpCustomFieldEntity.class);

        return list;
    }

    /**
     * 分页查询，在这个场景下，可能用不到。
     * @param erpCustomFieldDto
     * @return
     */
    @Override
    public PageInfoVO<ErpCustomFieldEntity> pageList(ErpCustomFieldDto erpCustomFieldDto) {
        if(erpCustomFieldDto == null || StringUtils.isEmpty(erpCustomFieldDto.getEid())){
            return new PageInfoVO<ErpCustomFieldEntity>(0, 0, 0l);
        }
        if(erpCustomFieldDto.getPageIndex() == null){
            erpCustomFieldDto.setPageIndex(1);
        }
        if(erpCustomFieldDto.getPageSize() == null){
            erpCustomFieldDto.setPageSize(8);
        }

        Criteria criteria = baseQueryCondition(erpCustomFieldDto.getEid());
        if(StringUtils.isNotEmpty(erpCustomFieldDto.getFromSystem())){
            criteria.and("fromSystem").is(erpCustomFieldDto.getFromSystem());
        }
        if(StringUtils.isNotEmpty(erpCustomFieldDto.getInterfaceType())){
            criteria.and("interfaceType").is(erpCustomFieldDto.getInterfaceType());
        }
        if(StringUtils.isNotEmpty(erpCustomFieldDto.getInterfaceEngName())){
            criteria.and("interfaceEngName").is(erpCustomFieldDto.getInterfaceEngName());
        }

        Query query=new Query();
        query.skip((erpCustomFieldDto.getPageIndex()-1)*erpCustomFieldDto.getPageSize()).limit(erpCustomFieldDto.getPageSize());
        query.addCriteria(criteria);
        query.with(new Sort(Sort.Direction.DESC,"createdTime"));

        long count = mongoTemplate.count(query, ErpCustomFieldEntity.class);
        List<ErpCustomFieldEntity> list = mongoTemplate.find(query, ErpCustomFieldEntity.class);
        return new PageInfoVO<ErpCustomFieldEntity>(erpCustomFieldDto.getPageIndex(), erpCustomFieldDto.getPageSize(), list, count);
    }

    @Override
    public long updateEntity(String id, ErpCustomFieldEntity erpCustomFieldEntity) {
        if(StringUtils.isEmpty(id) || erpCustomFieldEntity == null){
            return 0l;
        }
        Criteria criteria = baseQueryCondition();
        criteria.and("_id").is(id);
        Query query=new Query(criteria);
        Update update = new Update();

        if(StringUtils.isNotEmpty(erpCustomFieldEntity.getFromSystem())){
            criteria.and("fromSystem").is(erpCustomFieldEntity.getFromSystem());
        }
        if(StringUtils.isNotEmpty(erpCustomFieldEntity.getInterfaceType())){
            criteria.and("interfaceType").is(erpCustomFieldEntity.getInterfaceType());
        }
        if(StringUtils.isNotEmpty(erpCustomFieldEntity.getInterfaceEngName())){
            criteria.and("interfaceEngName").is(erpCustomFieldEntity.getInterfaceEngName());
        }
        if(StringUtils.isNotEmpty(erpCustomFieldEntity.getRemark())){
            criteria.and("remark").is(erpCustomFieldEntity.getRemark());
        }

        UpdateResult updateResult = mongoTemplate.updateFirst(query, update, ErpCustomFieldEntity.class);

        return updateResult.getModifiedCount();
    }
}
