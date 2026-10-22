package es.unizar.webeng.lab3

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

/**
 * Employee collection and item resources.
 *
 * Method properties follow RFC 9110 (HTTP Semantics), which obsoletes RFC 7231.
 */
@RestController
class EmployeeController(
    private val repository: EmployeeRepository,
) {
    /**
     * Lists every employee.
     *
     * GET is safe and idempotent (RFC 9110 §9.2.1, §9.2.2, §9.3.1).
     */
    @GetMapping("/employees")
    fun all(): Iterable<Employee> = repository.findAll()

    /**
     * Creates an employee in the collection.
     *
     * POST is neither safe nor idempotent (RFC 9110 §9.2.1, §9.2.2, §9.3.3).
     * The target is the collection. The server assigns the new resource's URI
     * and returns 201 Created (§15.3.2) with Location (§10.2.2).
     * A client-supplied id is discarded so a repeated POST cannot reuse it.
     */
    @PostMapping("/employees")
    fun newEmployee(
        @RequestBody newEmployee: Employee,
    ): ResponseEntity<Employee> {
        newEmployee.id = null       //Deletes the id so it's safe to insert in DB
        val employee = repository.save(newEmployee)
        val location =          //Gets current mapping of the URI, and creates the new URI for the new id for the employee
            ServletUriComponentsBuilder
                .fromCurrentServletMapping()
                .path("/employees/{id}")
                .build(employee.id)
        return ResponseEntity.created(location).body(employee)  //Variables are saved in threads
    }

    /**
     * Reads one employee.
     *
     * GET is safe and idempotent (RFC 9110 §9.2.1, §9.2.2, §9.3.1).
     * 200 OK (§15.3.1) when it exists. 404 Not Found (§15.5.5) when it does not.
     */
    @GetMapping("/employees/{id}")
    fun one(
        @PathVariable id: Long,
    ): Employee = repository.findById(id).orElseThrow { EmployeeNotFoundException(id) }

    /**
     * Replaces the employee at this URI, or creates it there.
     *
     * PUT is idempotent and not safe (RFC 9110 §9.2.1, §9.2.2, §9.3.4).
     * The request URI is the target resource. An id in the body is not.
     * 201 Created (§15.3.2) when this URI has no current representation.
     * Location may be omitted because that URI is the target (§15.3.2).
     * 200 OK (§15.3.1) when a later identical PUT stores the same state.
     * The status may change from 201 to 200. The resource state does not,
     * which is what idempotency requires (§9.2.2).
     * Content-Location (§8.7) identifies the representation in the body.
     */
    @PutMapping("/employees/{id}")
    fun replaceEmployee(
        @RequestBody newEmployee: Employee,
        @PathVariable id: Long,
    ): ResponseEntity<Employee> {
        val location =
            ServletUriComponentsBuilder
                .fromCurrentServletMapping()
                .path("/employees/{id}")
                .build(id)
                .toASCIIString()
        val (status, body) =
            repository
                .findById(id)
                .map { employee ->
                    employee.name = newEmployee.name
                    employee.role = newEmployee.role
                    repository.save(employee)
                    HttpStatus.OK to employee
                }.orElseGet {
                    newEmployee.id = id
                    repository.save(newEmployee)
                    HttpStatus.CREATED to newEmployee
                }
        return ResponseEntity.status(status).header("Content-Location", location).body(body)
    }

    /**
     * Removes the association between this URI and its employee.
     *
     * DELETE is idempotent and not safe (RFC 9110 §9.2.2, §9.3.5).
     * 204 No Content (§15.3.5): the action is done and the body is empty.
     * A repeated DELETE returns 204 again. The resource stays absent (§9.2.2).
     * Spring Data JPA's deleteById does nothing when the id is already gone,
     * so the second call does not fail.
     */
    @DeleteMapping("/employees/{id}")
    fun deleteEmployee(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        repository.deleteById(id)
        return ResponseEntity.noContent().build()
    }
}

/** No current representation for this employee URI (RFC 9110 §15.5.5). */
@ResponseStatus(HttpStatus.NOT_FOUND)
class EmployeeNotFoundException(
    id: Long,
) : Exception("Could not find employee $id")
