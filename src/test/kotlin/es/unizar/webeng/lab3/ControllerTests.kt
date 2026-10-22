package es.unizar.webeng.lab3

import com.ninjasquad.springmockk.MockkBean
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import java.util.Optional

private val MANAGER_REQUEST_BODY = { name: String ->
    """
    { 
        "role": "Manager", 
        "name": "$name" 
    }
    """
}

private val MANAGER_RESPONSE_BODY = { name: String, id: Int ->
    """
    { 
       "name" : "$name",
       "role" : "Manager",
       "id" : $id
    }
    """
}

private val MANAGER_LIST_BODY =
    """
    [{
       "name" : "Mary",
       "role" : "Manager",
       "id" : 1
    }]
    """

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ControllerTests {
    @Autowired
    private lateinit var mvc: MockMvc

    @MockkBean
    private lateinit var employeeRepository: EmployeeRepository

    @Test
    fun `POST is not safe and not idempotent`() {

        // Initial state: the collection is empty. No employee exists.
        // Runtime state: the first save stores Mary, Manager, as id 1. The second save stores Mary, Manager, as id 2.
        
        //save returns a new id on each call
        every {
            employeeRepository.save(any<Employee>())
        } answers {
            Employee("Mary", "Manager", 1)
        } andThenAnswer {
            Employee("Mary", "Manager", 2)
        }

        mvc
            .post("/employees") {
                contentType = MediaType.APPLICATION_JSON
                content = MANAGER_REQUEST_BODY("Mary")
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isCreated() }
                header { string("Location", "http://localhost/employees/1") }
                content {
                    contentType(MediaType.APPLICATION_JSON)
                    json(MANAGER_RESPONSE_BODY("Mary", 1))
                }
            }

        mvc
            .post("/employees") {
                contentType = MediaType.APPLICATION_JSON
                content = MANAGER_REQUEST_BODY("Mary")
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isCreated() }
                header { string("Location", "http://localhost/employees/2") }
                content {
                    contentType(MediaType.APPLICATION_JSON)
                    json(MANAGER_RESPONSE_BODY("Mary", 2))
                }
            }


        // Hint: What repository methods should be called for a POST operation?
        // What methods should NOT be called? Think about the difference between safe and unsafe operations.
        
        //Both saves discard the client id
        verify(exactly = 2) {
            employeeRepository.save(Employee("Mary", "Manager", null))
        }
        //No read or delete
        verify(exactly = 0) {
            employeeRepository.findById(any())
            employeeRepository.findAll()
            employeeRepository.deleteById(any())
        }
    }

    @Test
    fun `GET is safe and idempotent`() {
        // SETUP
        // Initial state: /employees/1 is Mary, Manager, id 1. /employees/2 has no representation.
        // Runtime state: both stay that way.
        
        //findById returns an employee for id 1
        every {
            employeeRepository.findById(1)
        } answers {
            Optional.of(Employee("Mary", "Manager", 1))
        }
        //findById returns empty for id 2
        every {
            employeeRepository.findById(2)
        } answers {
            Optional.empty()
        }

        mvc.get("/employees/1").andExpect {
            status { isOk() }
            content {
                contentType(MediaType.APPLICATION_JSON)
                json(MANAGER_RESPONSE_BODY("Mary", 1))
            }
        }

        mvc.get("/employees/1").andExpect {
            status { isOk() }
            content {
                contentType(MediaType.APPLICATION_JSON)
                json(MANAGER_RESPONSE_BODY("Mary", 1))
            }
        }

        mvc.get("/employees/2").andExpect {
            status { isNotFound() }
        }

        mvc.get("/employees/2").andExpect {
            status { isNotFound() }
        }

        // VERIFY
        // Hint: Since GET is safe, what repository methods should NOT be called?
        // Count how many times each method was called based on the test requests.
        
        //findById(1) was called twice
        verify(exactly = 2) {
            employeeRepository.findById(1)
        }
        //findById(2) was called twice
        verify(exactly = 2) {
            employeeRepository.findById(2)
        }
        //no modifying methods were called
        verify(exactly = 0) {
            employeeRepository.save(any())
            employeeRepository.deleteById(any())
            employeeRepository.findAll()
        }
    }

    @Test
    fun `PUT is idempotent but not safe`() {
        // SETUP
        // Initial state: /employees/1 has no representation.
        // Runtime state: the first PUT stores Tom, Manager, at id 1. The second PUT leaves that same employee.
        
        //findById is empty, then returns the employee
        every {
            employeeRepository.findById(1)
        } answers {
            Optional.empty()
        } andThenAnswer {
            Optional.of(Employee("Tom", "Manager", 1)) //This is answered when findById is called the 2nd time
        }
        
        //Save returns the employee at id 1
        every {
            employeeRepository.save(any<Employee>())
        } answers {
            Employee("Tom", "Manager", 1)
        }

        mvc
            .put("/employees/1") {
                contentType = MediaType.APPLICATION_JSON
                content = MANAGER_REQUEST_BODY("Tom")
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isCreated() }
                header { string("Content-Location", "http://localhost/employees/1") }
                content {
                    contentType(MediaType.APPLICATION_JSON)
                    json(MANAGER_RESPONSE_BODY("Tom", 1))
                }
            }

        mvc
            .put("/employees/1") {
                contentType = MediaType.APPLICATION_JSON
                content = MANAGER_REQUEST_BODY("Tom")
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isOk() }
                header { string("Content-Location", "http://localhost/employees/1") }
                content {
                    contentType(MediaType.APPLICATION_JSON)
                    json(MANAGER_RESPONSE_BODY("Tom", 1))
                }
            }

        // VERIFY
        // Hint: What repository methods should be called for PUT operations?
        // Think about the controller logic and how many times each method should be invoked.
        
        //both saves store the same employee
        verify(exactly = 2) {
            employeeRepository.save(Employee("Tom", "Manager", 1))
        }
        //no delete or list
        verify(exactly = 0) {
            employeeRepository.deleteById(any())
            employeeRepository.findAll()
        }
    }

    @Test
    fun `DELETE is idempotent but not safe`() {
        // SETUP
        // Initial state: /employees/1 is associated with an employee.
        // Runtime state: the first DELETE removes that association. The second DELETE finds it already gone.
        TODO("Complete the mock setup for DELETE test")
        mvc.delete("/employees/1").andExpect {
            status { isNoContent() }
        }

        mvc.delete("/employees/1").andExpect {
            status { isNoContent() }
        }

        // VERIFY
        // Hint: What repository methods should be called for DELETE operations?
        // What methods should NOT be called? Think about the nature of DELETE operations.
        TODO("Complete the verification for DELETE test")
    }

    @Test
    fun `GET collection is safe and idempotent`() {
        // SETUP - COMPLETE ME!
        // Initial state: the collection contains exactly Mary, Manager, id 1.
        // Runtime state: the second GET sees that same list.
        TODO("Complete the mock setup for the collection GET")

        mvc.get("/employees").andExpect {
            status { isOk() }
            content {
                contentType(MediaType.APPLICATION_JSON)
                json(MANAGER_LIST_BODY)
            }
        }

        mvc.get("/employees").andExpect {
            status { isOk() }
            content {
                contentType(MediaType.APPLICATION_JSON)
                json(MANAGER_LIST_BODY)
            }
        }

        // VERIFY - COMPLETE ME!
        // Hint: Count the list calls. A safe list must not save, delete, or fetch one id.
        TODO("Complete the verification for the collection GET")
    }
}
