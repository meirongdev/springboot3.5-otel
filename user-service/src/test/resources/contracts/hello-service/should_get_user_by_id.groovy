package contracts.user

org.springframework.cloud.contract.spec.Contract.make {
    name "should_get_user_by_id"
    description "Represents a successful response when fetching user by ID"
    
    request {
        method 'GET'
        url '/api/users/1'
    }
    
    response {
        status 200
        headers {
            header('Content-Type': 'application/json')
        }
        body([
            id: 1,
            name: 'Alice',
            email: 'alice@example.com'
        ])
    }
}
