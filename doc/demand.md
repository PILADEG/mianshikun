## QuestionController
```json
@PostMapping("/add")
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest) {
        // todo
        return null;
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest) {
        // todo
        return null;
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        // todo
        return null;
    }
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest) {
        // todo
        return null;
    }
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(Long id) {
        // todo
        return null;
    }

    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<?> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        // todo
        return null;
    }

    @PostMapping("/list/page/vo")
    public BaseResponse<?> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        // todo
        return null;
    }

    @PostMapping("/list/page/vo/sentinel")
    public BaseResponse<?> listQuestionVOByPageSentinel(@RequestBody QuestionQueryRequest questionQueryRequest) {
        // todo
        return null;
    }

    @PostMapping("/my/list/page/vo")
    public BaseResponse<?> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        // todo
        return null;
    }
    
    @PostMapping("/search/page/vo")
    public BaseResponse<?> searchQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        // todo
        return null;
    }
```
其中listQuestionByPage是给管理员使用不需要做限流，其它的批量查询功能限制一次查询的数量不得超过20.