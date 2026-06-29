package com.kun.mianshikun.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.kun.mianshikun.common.BaseResponse;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.common.ResultUtils;
import com.kun.mianshikun.constant.FileConstant;
import com.kun.mianshikun.exception.BusinessException;
import com.kun.mianshikun.model.dto.file.UploadFileRequest;
import com.kun.mianshikun.model.dto.questionBank.QuestionBankQueryRequest;
import com.kun.mianshikun.model.entity.QuestionBank;
import com.kun.mianshikun.model.enums.FileUploadBizEnum;
import com.kun.mianshikun.service.QuestionBankService;
import com.kun.mianshikun.util.UserContext;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

/**
 * 文件接口
 *
 * @author <a href="https://github.com/likun">程序员鱼皮</a>
 * @from <a href="https://kun.icu">编程导航知识星球</a>
 */
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {
    @Resource
    private QuestionBankService questionBankService;
    /**
     * 文件上传
     *
     * @param multipartFile
     * @param uploadFileRequest
     * @return
     */
    @PostMapping("/upload")
    public BaseResponse<String> uploadFile(@RequestPart("file") MultipartFile multipartFile,
            UploadFileRequest uploadFileRequest) {

        String biz = uploadFileRequest.getBiz();
        FileUploadBizEnum fileUploadBizEnum = FileUploadBizEnum.getEnumByValue(biz);
        if (fileUploadBizEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        validFile(multipartFile, fileUploadBizEnum);
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 文件目录：根据业务、用户来划分
        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String originalFilename = multipartFile.getOriginalFilename();
        String filename = uuid + "-" + originalFilename;
        // 相对路径（前端通过 StaticResourceConfig 访问）
        String relativePath = String.format("/%s/%s/%s", fileUploadBizEnum.getValue(), userId, filename);
        // 本地绝对路径
        String localDir = FileConstant.LOCAL_PATH + "/" + fileUploadBizEnum.getValue() + "/" + userId;
        try {
            File dir = new File(localDir);
            if (!dir.exists() && !dir.mkdirs()) {
                log.error("failed to create directory: {}", localDir);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "目录创建失败");
            }
            File localFile = new File(FileConstant.LOCAL_PATH + relativePath);
            multipartFile.transferTo(localFile);
            log.info("file saved to: {}", localFile.getAbsolutePath());
            return ResultUtils.success(relativePath);
        } catch (IOException e) {
            log.error("file write error, path = {}{}", localDir, filename, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件写入失败");
        }
    }
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteOldFile(@RequestBody QuestionBankQueryRequest questionBank){
        if (questionBank.getId() == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QuestionBank oldQuestionBank = questionBankService.getById(questionBank.getId());
        if (oldQuestionBank == null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        try{
            if (oldQuestionBank.getPicture() != null &&
                    !"".equals(oldQuestionBank.getPicture())){
                if (!oldQuestionBank.getPicture().startsWith("http")){
                    deleteFile(oldQuestionBank.getPicture());
                }
            }
            log.info("0000000000000000000000000");
            questionBankService.update(new UpdateWrapper<QuestionBank>()
                    .eq("id", questionBank.getId())
                    .set("picture", null));
            return ResultUtils.success(true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件删除失败");
        }
    }
    public void deleteFile(String relativePath) {
        if (relativePath == null &&
                "".equals(relativePath)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (relativePath.startsWith("http")){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        File file = new File(FileConstant.LOCAL_PATH + relativePath);
        if (file.exists()) {
            try {
                file.delete();
            } catch (Exception e) {
                log.error("file delete error, path = {}", relativePath, e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件删除失败");
            }
        }
    }
    /**
     * 校验文件
     *
     * @param multipartFile
     * @param fileUploadBizEnum 业务类型
     */
    private void validFile(MultipartFile multipartFile, FileUploadBizEnum fileUploadBizEnum) {
        // 文件大小
        long fileSize = multipartFile.getSize();
        // 文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        log.info("fileUploadBizEnum = {}, fileSize = {}, fileSuffix = {}", fileUploadBizEnum, fileSize, fileSuffix);
        final long ONE_M = 1024 * 1024L * 10L;
        if (FileUploadBizEnum.USER_AVATAR.equals(fileUploadBizEnum)
                || FileUploadBizEnum.QUESTION_BANK_PICTURE.equals(fileUploadBizEnum)) {
            if (fileSize > ONE_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 10M");
            }
            if (!Arrays.asList("jpeg", "jpg", "svg", "png", "webp").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
        }
    }
}
