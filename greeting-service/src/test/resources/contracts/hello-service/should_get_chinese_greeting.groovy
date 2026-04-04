package contracts.greeting

org.springframework.cloud.contract.spec.Contract.make {
    name "should_get_chinese_greeting"
    description "Represents a successful response for a Chinese greeting"
    
    request {
        method 'GET'
        url '/api/greetings'
        headers {
            header('Accept-Language', 'zh')
        }
    }
    
    response {
        status OK()
        headers {
            header('Content-Type': 'application/json')
        }
        body([
            language: 'zh',
            message: '你好，世界！'
        ])
    }
}
