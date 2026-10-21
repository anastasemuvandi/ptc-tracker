package rw.gov.ptc.evaluation.component;

import org.springframework.stereotype.Component;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.file.entity.ProcFile;
import rw.gov.ptc.file.repository.ProcFileRepository;
import rw.gov.ptc.security.Access;

import java.util.Map;
import java.util.UUID;

/**
 * Loads a file under RLS (another institution's file is simply not found)
 * and applies the committee-level read/write checks used by the evaluation
 * and contract-change modules.
 */
@Component
public class FileAccess {

    private final ProcFileRepository files;
    private final Access access;

    public FileAccess(ProcFileRepository files, Access access) {
        this.files = files;
        this.access = access;
    }

    public ProcFile read(UUID fileId) {
        ProcFile f = files.findById(fileId).orElseThrow(() -> ApiException.notFound("File"));
        access.requireCanRead(f.committeeId);
        return f;
    }

    /** Officers (Chairperson, Vice-Chairperson, Secretary) of the file's committee. */
    public ProcFile write(UUID fileId) {
        ProcFile f = files.findById(fileId).orElseThrow(() -> ApiException.notFound("File"));
        access.requireOfficer(f.committeeId);
        return f;
    }

    public static void requireType(ProcFile f, String... types) {
        for (String t : types) {
            if (t.equals(f.itemType)) {
                return;
            }
        }
        throw ApiException.rule("file.wrong_item_type", "This action does not apply to a type " + f.itemType + " file",
                Map.of("itemType", f.itemType, "expected", String.join(",", types)));
    }

    public static void checkVersion(Long requested, Long current) {
        if (requested != null && !requested.equals(current)) {
            throw ApiException.conflict("concurrent_modification",
                    "This record was changed by someone else. Reload and try again.");
        }
    }
}
