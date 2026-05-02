
package celtech.utils;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.openautomaker.base.configuration.fileRepresentation.CameraProfile;

import javafx.util.StringConverter;

/**
 *
 * @author tonya
 */
public class CameraProfileStringConverter extends StringConverter<CameraProfile> {
	Supplier<List<CameraProfile>> listSupplier;

	public CameraProfileStringConverter(Supplier<List<CameraProfile>> ls) {
		listSupplier = ls;
	}

	@Override
	public String toString(CameraProfile profile) {
		if (profile == null) {
			return "";
		}
		return profile.profileName;
	}

	@Override
	public CameraProfile fromString(String string) {
		CameraProfile profile = null;
		Optional<CameraProfile> cpOpt = listSupplier.get()
				.stream()
				.filter(p -> p.profileName.equalsIgnoreCase(string))
				.findFirst();
		// Unwrap optional as return type is "bare".
		if (cpOpt.isPresent())
			profile = cpOpt.get();
		return profile;
	}
}
