package com.lolita.app.ui.screen.`import`

/** Display source indices in adjacent deposit/balance groups, without mutating the source queue. */
internal fun importDisplayOrder(items: List<ImportItemState>): List<Int> {
    val visited = BooleanArray(items.size)
    return buildList(items.size) {
        for (index in items.indices) {
            if (visited[index]) continue
            val item = items[index]
            val peerIndex = item.pairedWith
            val peer = peerIndex?.let(items::getOrNull)
            val validPair = peerIndex != null && peerIndex != index && peer?.pairedWith == index &&
                ((item.paymentRole == PaymentRole.DEPOSIT && peer.paymentRole == PaymentRole.BALANCE) ||
                    (item.paymentRole == PaymentRole.BALANCE && peer.paymentRole == PaymentRole.DEPOSIT))
            if (validPair) {
                val deposit = if (item.paymentRole == PaymentRole.DEPOSIT) index else peerIndex!!
                val balance = if (item.paymentRole == PaymentRole.BALANCE) index else peerIndex!!
                add(deposit)
                add(balance)
                visited[deposit] = true
                visited[balance] = true
            } else {
                add(index)
                visited[index] = true
            }
        }
    }
}
