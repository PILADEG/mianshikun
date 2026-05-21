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
## QuestionBankController
```json
@PostMapping("/add")
    public BaseResponse<Long> addQuestionBank(@RequestBody QuestionBankAddRequest questionBankAddRequest) {
        // todo
        return null;
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestionBank(@RequestBody DeleteRequest deleteRequest) {
        // todo
        return null;
    }
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestionBank(@RequestBody QuestionBankEditRequest questionBankEditRequest) {
        // todo
        return null;
    }
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestionBank(@RequestBody QuestionBankUpdateRequest questionBankUpdateRequest) {
        // todo
        return null;
    }

    @GetMapping("/get/vo")
    public BaseResponse<QuestionBankVO> getQuestionBankVOById(Long id, Boolean needQueryQuestionList) {
        // todo
        return null;
    }

    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<?> listQuestionBankByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest) {
        // todo
        return null;
    }

    @PostMapping("/list/page/vo")
    public BaseResponse<?> listQuestionBankVOByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest) {
        // todo
        return null;
    }

    @PostMapping("/my/list/page/vo")
    public BaseResponse<?> listMyQuestionBankVOByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest) {
        // todo
        return null;
    }
```
其中listQuestionBankByPage为管理员使用，不需要限流，其它的批量查询功能限制一次查询的数量不得超过20.
## QuestionBankQuestionController
```json
@PostMapping("/add")
    public BaseResponse<Long> addQuestionBankQuestion(@RequestBody QuestionBankQuestionAddRequest addRequest) {
        // todo
        return null;
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestionBankQuestion(@RequestBody DeleteRequest deleteRequest) {
        // todo
        return null;
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestionBankQuestion(@RequestBody QuestionBankQuestionUpdateRequest updateRequest) {
        // todo
        return null;
    }

    @GetMapping("/get/vo")
    public BaseResponse<QuestionBankQuestionVO> getQuestionBankQuestionVOById(Long id) {
        // todo
        return null;
    }

    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<?> listQuestionBankQuestionByPage(@RequestBody QuestionBankQuestionQueryRequest queryRequest) {
        // todo
        return null;
    }

    @PostMapping("/list/page/vo")
    public BaseResponse<?> listQuestionBankQuestionVOByPage(@RequestBody QuestionBankQuestionQueryRequest queryRequest) {
        // todo
        return null;
    }

    @PostMapping("/my/list/page/vo")
    public BaseResponse<?> listMyQuestionBankQuestionVOByPage(@RequestBody QuestionBankQuestionQueryRequest queryRequest) {
        // todo
        return null;
    }
    
    @PostMapping("/remove")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> removeQuestionBankQuestion(@RequestBody QuestionBankQuestionRemoveRequest removeRequest) {
        // todo
        return null;
    }
```
其中listQuestionBankQuestionByPage为管理员使用，不需要限流，其它的批量查询功能限制一次查询的数量不得超过20.
## 注意
- **凡是要修改数据库的功能都要在service层上加上事务**
- **对于search、query这种的功能的接口需要在service层上手动做类型校验，校验单独分出一个方法方便复用**
- **若要抛出异常可借助ThrowUtils.java该工具类,你可以查看com/kun/mianshikun/exception文件夹下的文件来决定你该怎么抛出异常，但无论是什么异常，前端收到的一定不能是一大串错误代码或是其它乱七八糟的**
- **只有controller层的返回值是BaseResponse类型，service层应该只返回该接口里BaseResponse需要的data数据,以下为示例**
### controller层
```json
@GetMapping("/get/vo")
    public BaseResponse<PostVO> getPostVOById(long id, HttpServletRequest request) {
        //...existing code
        return ResultUtils.success(postService.getPostVO(post, request));
    }
```
### service层接口
```json
PostVO getPostVO(Post post, HttpServletRequest request);
```