package br.ufla.uuidcontract;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface UuidEntityContractRepository extends JpaRepository<UuidEntityContractFixture, UUID>,
        JpaSpecificationExecutor<UuidEntityContractFixture> {
}
