package fund.cyber.api.ethereum.functions

import fund.cyber.cassandra.ethereum.model.CqlEthereumContractTxPreview
import fund.cyber.cassandra.ethereum.repository.PageableEthereumContractTxRepository
import fund.cyber.common.toSearchHashFormat
import org.springframework.data.cassandra.core.query.CassandraPageRequest
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux

class ContractTxesByHash(
        private val contractTxRepository: PageableEthereumContractTxRepository
) : HandlerFunction<ServerResponse> {


    override fun handle(request: ServerRequest): Mono<ServerResponse> {

        val hash = request.pathVariable("hash")
        val page = request.queryParam("page").orElse("0").toInt()
        val pageSize = request.queryParam("pageSize").orElse("20").toInt()


        var slice = contractTxRepository
                .findAllByContractHash(hash.toSearchHashFormat(), CassandraPageRequest.first(pageSize))

        for (i in 1..page) {
            if (slice.hasNext()) {
                slice = contractTxRepository.findAllByContractHash(hash, slice.nextPageable())
            } else return ServerResponse.notFound().build()
        }
        return ServerResponse.ok().body(slice.content.toFlux(), CqlEthereumContractTxPreview::class.java)
    }
}
