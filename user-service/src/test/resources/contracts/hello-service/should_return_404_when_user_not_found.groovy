package contracts.user

org.springframework.cloud.contract.spec.Contract.make {
    name "should_return_404_when_user_not_found"
    description "Represents a 404 response when requesting a non-existent user"

    request {
        method 'GET'
        url '/api/users/999'
    }

    response {
        status 404
        headers {
            header('Content-Type': 'application/problem+json')
        }
        body([
            type: 'about:blank',
            title: 'User Not Found',
            detail: 'User not found with id: 999',
            status: 404,
            userId: 999
        ])
    }
}
